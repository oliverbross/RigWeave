// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/workflow_control.h"

#include <algorithm>
#include <cctype>
#include <charconv>
#include <sstream>

namespace rigweave::workflow_control {
namespace {
constexpr std::size_t MaxIdempotency = 2048;
bool validId(std::string_view value) {
  return value.size() >= 8 && value.size() <= 128 && std::all_of(value.begin(), value.end(), [](unsigned char c) { return std::isalnum(c) || c == '.' || c == '_' || c == ':' || c == '-'; });
}
bool validName(std::string_view value) {
  return !value.empty() && value.size() <= 96 && std::all_of(value.begin(), value.end(), [](unsigned char c) { return std::isalnum(c) || c == '.' || c == '_' || c == ':' || c == '-'; });
}
std::optional<double> number(const std::unordered_map<std::string, std::string> &values, const char *key) {
  const auto it = values.find(key); if (it == values.end()) return std::nullopt;
  try { std::size_t end{}; const double value = std::stod(it->second, &end); if (end != it->second.size()) return std::nullopt; return value; } catch (...) { return std::nullopt; }
}
}

Engine::Engine(bool debugNoRadio) { setDebugNoRadio(debugNoRadio); }
void Engine::setDebugNoRadio(bool enabled) {
  m_debugNoRadio = enabled; clearAuthority();
  m_context.stationId = enabled ? "m6-deterministic-station" : "unconfigured";
  m_context.radioProfileId = enabled ? "m6-fake-radio" : "none";
  m_context.frequencyHz = enabled ? 14'074'000 : 0;
  m_context.band = enabled ? "20m" : ""; m_context.mode = enabled ? "FT8" : "";
  m_context.audioRoute = enabled ? "fake-iq-1" : ""; m_context.connected = enabled;
  ++m_context.contextGeneration; ++m_context.agentGeneration;
}

Result Engine::execute(const Command &command, std::uint64_t nowMs) {
  const auto cached = m_idempotency.find(command.idempotencyKey);
  const std::string sig = signature(command);
  if (cached != m_idempotency.end()) return cached->second.signature == sig ? cached->second.result : reject("IDEMPOTENCY_CONFLICT");
  Result result;
  if (!valid(command)) result = reject("WORKFLOW_ENVELOPE_INVALID");
  else if (command.protocol.major != 1 || command.protocol.minor > 2) result = reject("WORKFLOW_PROTOCOL_INCOMPATIBLE");
  else if (command.expiresMs <= nowMs) result = reject("WORKFLOW_INTENT_EXPIRED");
  else if (command.contextGeneration != m_context.contextGeneration || command.agentGeneration != m_context.agentGeneration) result = reject("WORKFLOW_CONTEXT_STALE");
  else if (command.action == "global.stop") result = globalStop(nowMs);
  else if (command.role != Role::Operator) result = reject("OPERATOR_ROLE_REQUIRED");
  else if (command.action == "arm.tx") {
    if (!includes(command.capabilities, Capability::TxOperator)) result = reject("TX_OPERATOR_CAPABILITY_REQUIRED");
    else if (!m_debugNoRadio) result = reject("TX_UNAVAILABLE_PHYSICAL_ACCEPTANCE_REQUIRED");
    else { m_authority.txLeaseId = "tx-lease-" + std::to_string(m_counter++); m_authority.txArmId = "tx-arm-" + std::to_string(m_counter++); m_authority.expiresMs = nowMs + 60'000; result = accept("DEMO_TX_ACCEPTANCE_ARMED", {{"persistent","false"},{"physical","false"}}); }
  } else if (command.action == "arm.rotator") {
    if (!includes(command.capabilities, Capability::RotatorOperator)) result = reject("ROTATOR_OPERATOR_CAPABILITY_REQUIRED");
    else if (!m_debugNoRadio) result = reject("ROTATOR_UNAVAILABLE_PHYSICAL_ACCEPTANCE_REQUIRED");
    else { m_authority.rotatorLeaseId = "rotator-lease-" + std::to_string(m_counter++); m_authority.movementArmId = "movement-arm-" + std::to_string(m_counter++); m_authority.expiresMs = nowMs + 60'000; result = accept("DEMO_ROTATOR_ACCEPTANCE_ARMED", {{"persistent","false"},{"physical","false"}}); }
  } else if (command.action.find("tx") != std::string::npos || command.action.find("keyer") != std::string::npos || command.action.find("voice") != std::string::npos) {
    if (!includes(command.capabilities, Capability::TxOperator)) result = reject("TX_OPERATOR_CAPABILITY_REQUIRED");
    else if (!m_debugNoRadio || m_authority.txLeaseId.empty() || m_authority.txArmId.empty() || m_authority.expiresMs <= nowMs) result = reject("TX_UNAVAILABLE_PHYSICAL_ACCEPTANCE_REQUIRED");
    else result = accept("DEMO_TX_ACCEPTANCE", {{"rf","false"},{"ptt","false"},{"audio","DETERMINISTIC_FAKE"}});
  } else if (command.action.find("rotator") != std::string::npos || command.action.find("move") != std::string::npos) {
    if (!includes(command.capabilities, Capability::RotatorOperator)) result = reject("ROTATOR_OPERATOR_CAPABILITY_REQUIRED");
    else if (!m_debugNoRadio || m_authority.rotatorLeaseId.empty() || m_authority.movementArmId.empty() || m_authority.expiresMs <= nowMs) result = reject("ROTATOR_UNAVAILABLE_PHYSICAL_ACCEPTANCE_REQUIRED");
    else {
      const auto azimuth = number(command.arguments, "azimuth"); const auto elevation = number(command.arguments, "elevation");
      if (!azimuth || !elevation || *azimuth < 0.0 || *azimuth > 450.0 || *elevation < 0.0 || *elevation > 180.0) result = reject("ROTATOR_LIMIT_REJECTED");
      else result = accept("DEMO_ROTATOR_ACCEPTANCE", {{"azimuth",std::to_string(*azimuth)},{"elevation",std::to_string(*elevation)},{"physicalMovement","false"}});
    }
  } else if (command.action.find("provider") != std::string::npos) {
    result = includes(command.capabilities, Capability::ProviderAuthor) && m_debugNoRadio ? accept("FAKE_PROVIDER_OUTBOX_ACCEPTED", {{"realMutation","false"}}) : reject("PROVIDER_AUTHOR_REQUIRED");
  } else if (command.action.find("groups") != std::string::npos && command.action.find("send") != std::string::npos) {
    result = includes(command.capabilities, Capability::GroupsAuthor) && m_debugNoRadio ? accept("FAKE_GROUPS_OUTBOX_ACCEPTED", {{"realSend","false"}}) : reject("GROUPS_AUTHOR_REQUIRED");
  } else if (command.action.find("n1mm") != std::string::npos) {
    result = includes(command.capabilities, Capability::N1mmOperator) && m_debugNoRadio ? accept("FAKE_N1MM_ACCEPTED", {{"network","loopback-fake"}}) : reject("N1MM_OPERATOR_REQUIRED");
  } else result = accept("WORKFLOW_REVIEW_ACCEPTED", {{"reviewRequired","true"}});
  remember(command, result); return result;
}

Result Engine::globalStop(std::uint64_t) { clearAuthority(); m_context.transmitting = false; ++m_context.contextGeneration; return accept("GLOBAL_STOPPED", {{"digi","STOPPED"},{"keyer","STOPPED"},{"voice","STOPPED"},{"rotator","STOPPED"}}); }
void Engine::invalidate(std::string_view) { clearAuthority(); ++m_context.contextGeneration; }
void Engine::expire(std::uint64_t nowMs) { if (m_authority.expiresMs != 0 && m_authority.expiresMs <= nowMs) clearAuthority(); }
Result Engine::reject(std::string code) const { return {false,std::move(code),"REJECTED",m_context.contextGeneration,{}}; }
Result Engine::accept(std::string code, std::unordered_map<std::string,std::string> readback) const { return {true,std::move(code),"READBACK_CONFIRMED",m_context.contextGeneration,std::move(readback)}; }
bool Engine::valid(const Command &command) const { return validId(command.requestId) && validId(command.idempotencyKey) && validId(command.operatorSessionId) && validName(command.domain) && validName(command.action) && command.reason.size() <= 240 && command.arguments.size() <= 32; }
std::string Engine::signature(const Command &command) const { std::ostringstream out; out << command.protocol.major << '.' << command.protocol.minor << '|' << command.requestId << '|' << command.domain << '|' << command.action << '|' << command.operatorSessionId << '|' << static_cast<std::uint32_t>(command.capabilities) << '|' << command.contextGeneration << '|' << command.agentGeneration << '|' << command.expiresMs; std::vector<std::pair<std::string,std::string>> values(command.arguments.begin(),command.arguments.end()); std::sort(values.begin(),values.end()); for (const auto &[key,value] : values) out << '|' << key << '=' << value; return out.str(); }
void Engine::remember(const Command &command, const Result &result) { m_idempotency[command.idempotencyKey] = {signature(command),result}; m_order.push_back(command.idempotencyKey); while (m_order.size() > MaxIdempotency) { m_idempotency.erase(m_order.front()); m_order.erase(m_order.begin()); } }
void Engine::clearAuthority() { m_authority = {}; }
std::string_view capabilityName(Capability capability) { switch (capability) { case Capability::TxOperator:return "TX_OPERATOR_CAPABILITY"; case Capability::RotatorOperator:return "ROTATOR_OPERATOR_CAPABILITY"; case Capability::ProviderAuthor:return "PROVIDER_AUTHOR"; case Capability::GroupsAuthor:return "GROUPS_AUTHOR"; case Capability::N1mmOperator:return "N1MM_OPERATOR"; case Capability::None:return "NONE"; } return "UNKNOWN"; }
} // namespace rigweave::workflow_control
