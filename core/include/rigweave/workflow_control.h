// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace rigweave::workflow_control {

struct ProtocolVersion { std::uint16_t major{1}; std::uint16_t minor{2}; };
enum class Role { Observer, LocalOwner, Operator };
enum class Capability : std::uint32_t {
  None = 0, TxOperator = 1U << 0U, RotatorOperator = 1U << 1U,
  ProviderAuthor = 1U << 2U, GroupsAuthor = 1U << 3U, N1mmOperator = 1U << 4U,
};
constexpr Capability operator|(Capability left, Capability right) {
  return static_cast<Capability>(static_cast<std::uint32_t>(left) | static_cast<std::uint32_t>(right));
}
constexpr bool includes(Capability set, Capability value) {
  return (static_cast<std::uint32_t>(set) & static_cast<std::uint32_t>(value)) == static_cast<std::uint32_t>(value);
}

struct OperatingContext {
  std::uint64_t contextGeneration{1};
  std::uint64_t agentGeneration{1};
  std::string stationId{"unconfigured"};
  std::string radioProfileId{"none"};
  std::string receiverId{"rx-1"};
  std::uint64_t radioGeneration{1};
  std::int64_t frequencyHz{};
  std::string band;
  std::string mode;
  std::string audioRoute;
  std::string contestSessionId;
  std::string portableSessionId;
  std::string selectedDxId;
  std::string selectedSatelliteId;
  std::string rotatorAssignmentId;
  bool connected{};
  bool transmitting{};
};

struct RuntimeAuthority {
  std::string txLeaseId;
  std::string txArmId;
  std::string rotatorLeaseId;
  std::string movementArmId;
  std::uint64_t expiresMs{};
};

struct Command {
  ProtocolVersion protocol;
  std::string requestId;
  std::string idempotencyKey;
  std::string domain;
  std::string action;
  std::string operatorSessionId;
  Role role{Role::Observer};
  Capability capabilities{Capability::None};
  std::uint64_t contextGeneration{};
  std::uint64_t agentGeneration{};
  std::uint64_t expiresMs{};
  std::string reason;
  std::unordered_map<std::string, std::string> arguments;
};

struct Result {
  bool accepted{};
  std::string code;
  std::string state{"REJECTED"};
  std::uint64_t contextGeneration{};
  std::unordered_map<std::string, std::string> readback;
};

class Engine {
public:
  explicit Engine(bool debugNoRadio = false);
  void setDebugNoRadio(bool enabled);
  const OperatingContext &context() const { return m_context; }
  const RuntimeAuthority &authority() const { return m_authority; }
  ProtocolVersion protocol() const { return {}; }
  bool debugNoRadio() const { return m_debugNoRadio; }
  Result execute(const Command &command, std::uint64_t nowMs);
  Result globalStop(std::uint64_t nowMs);
  void invalidate(std::string_view reason);
  void expire(std::uint64_t nowMs);

private:
  Result reject(std::string code) const;
  Result accept(std::string code, std::unordered_map<std::string, std::string> readback = {}) const;
  bool valid(const Command &command) const;
  std::string signature(const Command &command) const;
  void remember(const Command &command, const Result &result);
  void clearAuthority();
  bool m_debugNoRadio{};
  OperatingContext m_context;
  RuntimeAuthority m_authority;
  std::uint64_t m_counter{1};
  struct Cached { std::string signature; Result result; };
  std::unordered_map<std::string, Cached> m_idempotency;
  std::vector<std::string> m_order;
};

std::string_view capabilityName(Capability capability);
} // namespace rigweave::workflow_control
