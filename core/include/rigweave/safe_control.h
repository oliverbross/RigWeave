// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace rigweave::safe_control {

inline constexpr std::uint16_t ProtocolMajor = 1;
inline constexpr std::uint16_t ProtocolMinor = 1;
inline constexpr std::uint64_t MinLeaseTtlMs = 1'000;
inline constexpr std::uint64_t MaxLeaseTtlMs = 30'000;
inline constexpr std::size_t MaxIdempotencyEntries = 4096;

enum class CommandClass : std::uint8_t {
  Read, Connection, SafeReceiveSet, AudioPresentation, AgentRxRuntime,
  GlobalStop, TransmitUnavailable, RotatorUnavailable
};

enum class CommandState : std::uint8_t {
  Draft, ReviewRequired, Submitted, AgentAccepted, Executing,
  ReadbackConfirmed, Rejected, TimedOut, Cancelled, Preempted,
  ConnectionLost, ReadbackMismatch, RecoveryRequired
};

struct RadioProfile {
  std::string id;
  std::string manufacturer;
  std::string model;
  std::string backend;
  std::string transport;
  std::string deviceIdentityHash;
  std::string acceptance;
  std::vector<std::string> capabilities;
};

struct WriterLease {
  std::string id;
  std::string stationId;
  std::string radioProfileId;
  std::string operatorSessionId;
  std::string controlWindowId;
  std::uint64_t agentGeneration{};
  std::uint64_t issuedMs{};
  std::uint64_t expiresMs{};
  std::uint64_t ttlMs{};
  std::string reason;
};

struct Command {
  std::string commandId;
  std::string idempotencyKey;
  std::string stationId;
  std::string radioProfileId;
  std::string operatorSessionId;
  std::string writerLeaseId;
  std::string controlWindowId;
  std::uint64_t agentGeneration{};
  std::uint64_t expectedRadioGeneration{};
  std::uint64_t expiresMs{};
  CommandClass commandClass{CommandClass::Read};
  std::string operation;
  std::unordered_map<std::string, std::string> arguments;
  std::string reason;
};

struct Result {
  bool accepted{};
  std::string code;
  CommandState state{CommandState::Rejected};
  std::uint64_t agentGeneration{};
  std::uint64_t radioGeneration{};
  std::unordered_map<std::string, std::string> readback;
  bool partial{};
  std::string recovery{"NONE"};
};

struct RuntimeState {
  std::uint64_t agentGeneration{1};
  std::uint64_t radioGeneration{1};
  std::string selectedProfileId{"fake-kx3"};
  std::string connection{"DISCONNECTED"};
  std::uint64_t frequencyHz{14'074'000};
  std::string mode{"USB"};
  std::uint32_t passbandHz{2400};
  std::string vfo{"VFOA"};
  std::int32_t ritHz{};
  bool split{};
  int afGain{35};
  int rfGain{75};
  int squelch{};
  std::string agc{"MED"};
  std::string scanner{"STOPPED"};
  std::string recording{"STOPPED"};
  std::string timeShift{"LIVE"};
  std::string replay{"STOPPED"};
  std::size_t receiverCount{};
  std::size_t monitorCount{};
  std::string calibration{"RELATIVE"};
  int surveyRetentionDays{30};
};

class Engine final {
public:
  explicit Engine(bool debugNoRadio = false);
  const std::vector<RadioProfile> &profiles() const { return m_profiles; }
  const RuntimeState &state() const { return m_state; }
  const std::optional<WriterLease> &lease() const { return m_lease; }
  bool debugNoRadio() const { return m_debugNoRadio; }
  void setDebugNoRadio(bool enabled) { m_debugNoRadio = enabled; }

  std::optional<WriterLease> acquireLease(std::string_view stationId,
      std::string_view profileId, std::string_view operatorSessionId,
      std::string_view controlWindowId, std::uint64_t nowMs,
      std::uint64_t ttlMs, std::string_view reason);
  bool renewLease(std::string_view leaseId, std::string_view operatorSessionId,
      std::string_view controlWindowId, std::uint64_t nowMs, std::uint64_t ttlMs);
  void releaseLease(std::string_view leaseId);
  void expire(std::uint64_t nowMs);
  void localPreempt();
  Result globalStop(std::uint64_t nowMs);
  Result execute(const Command &command, std::uint64_t nowMs);

private:
  bool hasLease(const Command &command, std::uint64_t nowMs) const;
  bool validCommand(const Command &command, std::uint64_t nowMs, std::string *code) const;
  Result reject(std::string code) const;
  Result confirmed(std::unordered_map<std::string, std::string> readback = {});
  std::string signature(const Command &command) const;
  void remember(const std::string &key, const std::string &signature, const Result &result);

  struct Remembered { std::string signature; Result result; };
  bool m_debugNoRadio{};
  RuntimeState m_state;
  std::vector<RadioProfile> m_profiles;
  std::optional<WriterLease> m_lease;
  std::unordered_map<std::string, Remembered> m_idempotency;
  std::vector<std::string> m_idempotencyOrder;
  std::uint64_t m_leaseCounter{};
};

std::string commandStateName(CommandState state);
std::string commandClassName(CommandClass value);

} // namespace rigweave::safe_control
