// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/safe_control.h"

#include <algorithm>
#include <array>
#include <charconv>
#include <sstream>

namespace rigweave::safe_control {
namespace {

bool validId(std::string_view value) {
  if (value.size() < 8 || value.size() > 128) return false;
  return std::all_of(value.begin(), value.end(), [](unsigned char c) {
    return std::isalnum(c) || c == '.' || c == '_' || c == ':' || c == '-';
  });
}

bool forbidden(std::string_view operation) {
  static constexpr std::array<std::string_view, 11> words{
      "ptt", "tune", "transmit", "tx.", "txAudio", "txPower",
      "drive", "key", "voiceTx", "digiTx", "rotator"};
  return std::any_of(words.begin(), words.end(), [&](std::string_view word) {
    return operation.find(word) != std::string_view::npos;
  });
}

template <typename T> std::optional<T> number(const std::unordered_map<std::string, std::string> &args,
                                               std::string_view key) {
  const auto found = args.find(std::string(key));
  if (found == args.end()) return std::nullopt;
  T value{};
  const auto parsed = std::from_chars(found->second.data(), found->second.data() + found->second.size(), value);
  if (parsed.ec != std::errc{} || parsed.ptr != found->second.data() + found->second.size()) return std::nullopt;
  return value;
}

bool acceptedMode(std::string_view mode) {
  static constexpr std::array<std::string_view, 12> modes{
      "USB", "LSB", "DIGU", "DIGL", "CW", "DSB", "AM", "SAM", "NFM", "WFM", "SPECTRUM", "FM"};
  return std::find(modes.begin(), modes.end(), mode) != modes.end();
}

} // namespace

Engine::Engine(bool debugNoRadio) : m_debugNoRadio(debugNoRadio) {
  const std::vector<std::string> full{"CONNECT", "FREQUENCY", "MODE", "FILTER", "VFO", "RIT", "SPLIT",
      "AF_GAIN", "RF_GAIN", "SQUELCH", "AGC", "RX_EQ", "AUDIO_RX", "PANADAPTER", "LOCAL_RECEIVER",
      "SCANNER", "RECORDING", "TIME_SHIFT", "REPLAY", "MEASUREMENTS", "CALIBRATION", "SURVEY", "GLOBAL_STOP"};
  m_profiles = {
      {"fake-kx3", "Elecraft", "KX3", "DETERMINISTIC_FAKE", "LOOPBACK", "fake:elecraft:kx3", "FAKE_ACCEPTED", full},
      {"kx2", "Elecraft", "KX2", "NATIVE", "SERIAL", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
      {"kx3", "Elecraft", "KX3", "NATIVE", "SERIAL", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
      {"flex", "FlexRadio", "FlexRadio", "FLEX", "NETWORK", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
      {"qmx", "QRP Labs", "QMX", "NATIVE", "SERIAL", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
      {"qmx-plus", "QRP Labs", "QMX+", "NATIVE", "SERIAL", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
      {"rgo-v6", "RGO", "RGO ONE V6", "NATIVE", "SERIAL", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
      {"rgo-legacy", "RGO", "RGO legacy", "NATIVE", "SERIAL", "unassigned", "READ_ONLY", {"READ"}},
      {"tci", "TCI", "TCI", "TCI", "NETWORK", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
      {"hamlib", "Hamlib", "Generic catalogue", "HAMLIB", "AGENT_REGISTRY", "unassigned", "SOURCE_COMPLETE_PHYSICAL_PENDING", full},
  };
}

std::optional<WriterLease> Engine::acquireLease(std::string_view stationId,
    std::string_view profileId, std::string_view operatorSessionId,
    std::string_view controlWindowId, std::uint64_t nowMs,
    std::uint64_t ttlMs, std::string_view reason) {
  expire(nowMs);
  if (!validId(stationId) || !validId(profileId) || !validId(operatorSessionId) ||
      !validId(controlWindowId) || reason.empty() || reason.size() > 160 ||
      ttlMs < MinLeaseTtlMs || ttlMs > MaxLeaseTtlMs || m_lease) return std::nullopt;
  WriterLease lease{"lease-" + std::to_string(++m_leaseCounter), std::string(stationId),
      std::string(profileId), std::string(operatorSessionId), std::string(controlWindowId),
      m_state.agentGeneration, nowMs, nowMs + ttlMs, ttlMs, std::string(reason)};
  m_lease = lease;
  return lease;
}

bool Engine::renewLease(std::string_view leaseId, std::string_view operatorSessionId,
    std::string_view controlWindowId, std::uint64_t nowMs, std::uint64_t ttlMs) {
  expire(nowMs);
  if (!m_lease || m_lease->id != leaseId || m_lease->operatorSessionId != operatorSessionId ||
      m_lease->controlWindowId != controlWindowId || ttlMs < MinLeaseTtlMs || ttlMs > MaxLeaseTtlMs) return false;
  m_lease->issuedMs = nowMs;
  m_lease->expiresMs = nowMs + ttlMs;
  m_lease->ttlMs = ttlMs;
  return true;
}

void Engine::releaseLease(std::string_view leaseId) { if (m_lease && m_lease->id == leaseId) m_lease.reset(); }
void Engine::expire(std::uint64_t nowMs) { if (m_lease && m_lease->expiresMs <= nowMs) m_lease.reset(); }
void Engine::localPreempt() { m_lease.reset(); ++m_state.agentGeneration; }

Result Engine::globalStop(std::uint64_t) {
  m_lease.reset();
  m_state.scanner = "STOPPED";
  m_state.recording = "STOPPED";
  m_state.timeShift = "LIVE";
  m_state.replay = "STOPPED";
  m_state.receiverCount = 0;
  m_state.monitorCount = 0;
  ++m_state.agentGeneration;
  ++m_state.radioGeneration;
  return confirmed({{"globalStop", "CONFIRMED"}, {"scanner", "STOPPED"}, {"recording", "STOPPED"}, {"transmit", "UNAVAILABLE"}});
}

bool Engine::hasLease(const Command &command, std::uint64_t nowMs) const {
  return m_lease && m_lease->expiresMs > nowMs && m_lease->id == command.writerLeaseId &&
      m_lease->operatorSessionId == command.operatorSessionId &&
      m_lease->controlWindowId == command.controlWindowId &&
      m_lease->radioProfileId == command.radioProfileId;
}

bool Engine::validCommand(const Command &command, std::uint64_t nowMs, std::string *code) const {
  if (!validId(command.commandId) || !validId(command.idempotencyKey) || !validId(command.stationId) ||
      !validId(command.radioProfileId) || !validId(command.operatorSessionId) || command.reason.empty() ||
      command.reason.size() > 160 || command.arguments.size() > 64) { *code = "COMMAND_INVALID"; return false; }
  if (command.expiresMs <= nowMs) { *code = "COMMAND_EXPIRED"; return false; }
  if (command.agentGeneration != m_state.agentGeneration) { *code = "STALE_AGENT_GENERATION"; return false; }
  if (command.expectedRadioGeneration != m_state.radioGeneration) { *code = "STALE_RADIO_GENERATION"; return false; }
  if (forbidden(command.operation)) { *code = "TRANSMIT_OR_MOVEMENT_UNAVAILABLE"; return false; }
  if (command.commandClass != CommandClass::GlobalStop && command.commandClass != CommandClass::Connection &&
      command.commandClass != CommandClass::AudioPresentation && command.commandClass != CommandClass::AgentRxRuntime &&
      !hasLease(command, nowMs)) { *code = "WRITER_LEASE_REQUIRED"; return false; }
  return true;
}

Result Engine::execute(const Command &command, std::uint64_t nowMs) {
  expire(nowMs);
  const std::string sig = signature(command);
  if (const auto prior = m_idempotency.find(command.idempotencyKey); prior != m_idempotency.end()) {
    return prior->second.signature == sig ? prior->second.result : reject("IDEMPOTENCY_CONFLICT");
  }
  std::string code;
  if (!validCommand(command, nowMs, &code)) { const Result result = reject(code); remember(command.idempotencyKey, sig, result); return result; }
  if (command.operation == "global.stop") { const Result result = globalStop(nowMs); remember(command.idempotencyKey, sig, result); return result; }
  if (!m_debugNoRadio && command.radioProfileId == "fake-kx3") { const Result result = reject("FAKE_PROFILE_DISABLED"); remember(command.idempotencyKey, sig, result); return result; }

  std::unordered_map<std::string, std::string> readback;
  const auto operation = command.operation;
  if (operation == "radio.connect") { m_state.connection = "CONNECTED"; m_state.selectedProfileId = command.radioProfileId; readback["connection"] = m_state.connection; }
  else if (operation == "radio.disconnect") { m_state.connection = "DISCONNECTED"; m_lease.reset(); readback["connection"] = m_state.connection; }
  else if (operation == "radio.set.frequency") {
    const auto hz = number<std::uint64_t>(command.arguments, "frequencyHz");
    if (!hz || *hz < 100'000 || *hz > 6'000'000'000ULL) { const Result result = reject("FREQUENCY_OUT_OF_RANGE"); remember(command.idempotencyKey, sig, result); return result; }
    m_state.frequencyHz = *hz; readback["frequencyHz"] = std::to_string(*hz);
  } else if (operation == "radio.set.mode") {
    const auto found = command.arguments.find("mode");
    if (found == command.arguments.end() || !acceptedMode(found->second)) { const Result result = reject("MODE_UNAVAILABLE"); remember(command.idempotencyKey, sig, result); return result; }
    m_state.mode = found->second; readback["mode"] = m_state.mode;
  } else if (operation == "radio.set.filter") {
    const auto width = number<std::uint32_t>(command.arguments, "passbandHz");
    if (!width || *width < 50 || *width > 20'000) { const Result result = reject("FILTER_OUT_OF_RANGE"); remember(command.idempotencyKey, sig, result); return result; }
    m_state.passbandHz = *width; readback["passbandHz"] = std::to_string(*width);
  } else if (operation == "radio.set.vfo") {
    const auto found = command.arguments.find("vfo");
    if (found == command.arguments.end() || (found->second != "VFOA" && found->second != "VFOB")) { const Result result = reject("VFO_UNAVAILABLE"); remember(command.idempotencyKey, sig, result); return result; }
    m_state.vfo = found->second; readback["vfo"] = m_state.vfo;
  } else if (operation == "radio.set.rit") { const auto value = number<std::int32_t>(command.arguments, "ritHz"); if (!value || *value < -20'000 || *value > 20'000) return reject("RIT_OUT_OF_RANGE"); m_state.ritHz = *value; readback["ritHz"] = std::to_string(*value); }
  else if (operation == "radio.set.split") { m_state.split = command.arguments.count("enabled") && command.arguments.at("enabled") == "true"; readback["split"] = m_state.split ? "true" : "false"; }
  else if (operation == "radio.set.afGain" || operation == "radio.set.rfGain" || operation == "radio.set.squelch") {
    const auto value = number<int>(command.arguments, "value"); if (!value || *value < 0 || *value > 100) return reject("GAIN_OUT_OF_RANGE");
    if (operation == "radio.set.afGain") m_state.afGain = *value; else if (operation == "radio.set.rfGain") m_state.rfGain = *value; else m_state.squelch = *value;
    readback["value"] = std::to_string(*value);
  } else if (operation == "radio.set.agc") { m_state.agc = command.arguments.count("value") ? command.arguments.at("value") : "MED"; readback["agc"] = m_state.agc; }
  else if (operation == "receiver.add") { if (m_state.receiverCount >= 2) return reject("RECEIVER_LIMIT"); ++m_state.receiverCount; readback["receiverCount"] = std::to_string(m_state.receiverCount); }
  else if (operation == "receiver.remove") { if (m_state.receiverCount) --m_state.receiverCount; readback["receiverCount"] = std::to_string(m_state.receiverCount); }
  else if (operation == "monitor.configure") { const auto count = number<std::size_t>(command.arguments, "count"); if (!count || *count > 4) return reject("MONITOR_LIMIT"); m_state.monitorCount = *count; readback["monitorCount"] = std::to_string(*count); }
  else if (operation.rfind("scanner.", 0) == 0) { m_state.scanner = operation.substr(8); std::transform(m_state.scanner.begin(), m_state.scanner.end(), m_state.scanner.begin(), ::toupper); readback["scanner"] = m_state.scanner; }
  else if (operation == "recording.start") { m_state.recording = "RECORDING"; readback["recording"] = m_state.recording; }
  else if (operation == "recording.stop") { m_state.recording = "STOPPED"; readback["recording"] = m_state.recording; }
  else if (operation.rfind("timeshift.", 0) == 0) { m_state.timeShift = operation == "timeshift.live" ? "LIVE" : operation == "timeshift.pause" ? "PAUSED" : "HISTORICAL"; readback["timeShift"] = m_state.timeShift; }
  else if (operation.rfind("replay.", 0) == 0) { m_state.replay = operation.substr(7); std::transform(m_state.replay.begin(), m_state.replay.end(), m_state.replay.begin(), ::toupper); readback["replay"] = m_state.replay; }
  else if (operation == "calibration.apply") { m_state.calibration = "CALIBRATED_BY_USER"; readback["calibration"] = m_state.calibration; }
  else if (operation == "survey.retention") { const auto days = number<int>(command.arguments, "days"); if (!days || (*days != 7 && *days != 30 && *days != 90)) return reject("RETENTION_INVALID"); m_state.surveyRetentionDays = *days; readback["days"] = std::to_string(*days); }
  else if (operation == "eq.rx.apply") { readback["rxEq"] = "READBACK_CONFIRMED"; }
  else if (operation == "eq.tx.apply") return reject("TX_EQ_APPLY_UNAVAILABLE");
  else { readback["operation"] = operation; }

  ++m_state.radioGeneration;
  Result result = confirmed(std::move(readback));
  remember(command.idempotencyKey, sig, result);
  return result;
}

Result Engine::reject(std::string code) const { return {false, std::move(code), CommandState::Rejected, m_state.agentGeneration, m_state.radioGeneration, {}, false, "NONE"}; }
Result Engine::confirmed(std::unordered_map<std::string, std::string> readback) { return {true, "READBACK_CONFIRMED", CommandState::ReadbackConfirmed, m_state.agentGeneration, m_state.radioGeneration, std::move(readback), false, "NONE"}; }

std::string Engine::signature(const Command &command) const {
  std::vector<std::pair<std::string, std::string>> args(command.arguments.begin(), command.arguments.end());
  std::sort(args.begin(), args.end());
  std::ostringstream out;
  out << command.operation << '|' << command.stationId << '|' << command.radioProfileId << '|' << command.agentGeneration << '|' << command.expectedRadioGeneration;
  for (const auto &[key, value] : args) out << '|' << key << '=' << value;
  return out.str();
}

void Engine::remember(const std::string &key, const std::string &sig, const Result &result) {
  if (m_idempotency.size() >= MaxIdempotencyEntries && !m_idempotencyOrder.empty()) { m_idempotency.erase(m_idempotencyOrder.front()); m_idempotencyOrder.erase(m_idempotencyOrder.begin()); }
  m_idempotency[key] = {sig, result}; m_idempotencyOrder.push_back(key);
}

std::string commandStateName(CommandState state) {
  static constexpr std::array<std::string_view, 13> names{"DRAFT", "REVIEW_REQUIRED", "SUBMITTED", "AGENT_ACCEPTED", "EXECUTING", "READBACK_CONFIRMED", "REJECTED", "TIMED_OUT", "CANCELLED", "PREEMPTED", "CONNECTION_LOST", "READBACK_MISMATCH", "RECOVERY_REQUIRED"};
  return std::string(names.at(static_cast<std::size_t>(state)));
}
std::string commandClassName(CommandClass value) {
  static constexpr std::array<std::string_view, 8> names{"READ", "CONNECTION", "SAFE_RECEIVE_SET", "AUDIO_PRESENTATION", "AGENT_RX_RUNTIME", "GLOBAL_STOP", "TRANSMIT_UNAVAILABLE", "ROTATOR_UNAVAILABLE"};
  return std::string(names.at(static_cast<std::size_t>(value)));
}

} // namespace rigweave::safe_control
