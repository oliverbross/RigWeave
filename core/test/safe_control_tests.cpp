// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/safe_control.h"

#include <cassert>
#include <iostream>

using namespace rigweave::safe_control;

Command command(const Engine &engine, std::string id, std::string operation, CommandClass kind, std::uint64_t now) {
  Command value;
  value.commandId = "command-" + id;
  value.idempotencyKey = "idem-key-" + id;
  value.stationId = "station-fixture";
  value.radioProfileId = "fake-kx3";
  value.operatorSessionId = "operator-session";
  value.controlWindowId = "control-window-a";
  value.agentGeneration = engine.state().agentGeneration;
  value.expectedRadioGeneration = engine.state().radioGeneration;
  value.expiresMs = now + 5'000;
  value.commandClass = kind;
  value.operation = std::move(operation);
  value.reason = "deterministic no-radio acceptance";
  if (engine.lease()) value.writerLeaseId = engine.lease()->id;
  return value;
}

int main() {
  constexpr std::uint64_t now = 1'000'000;
  Engine engine(true);
  assert(engine.profiles().size() >= 10);
  const auto lease = engine.acquireLease("station-fixture", "fake-kx3", "operator-session", "control-window-a", now, 5'000, "fixture");
  assert(lease);

  auto frequency = command(engine, "frequency", "radio.set.frequency", CommandClass::SafeReceiveSet, now);
  frequency.arguments["frequencyHz"] = "7074000";
  const auto applied = engine.execute(frequency, now + 10);
  assert(applied.accepted && engine.state().frequencyHz == 7'074'000);
  assert(engine.execute(frequency, now + 20).radioGeneration == applied.radioGeneration);
  frequency.arguments["frequencyHz"] = "14074000";
  assert(engine.execute(frequency, now + 30).code == "IDEMPOTENCY_CONFLICT");

  auto ptt = command(engine, "ptt", "radio.ptt", CommandClass::SafeReceiveSet, now);
  assert(!engine.execute(ptt, now + 40).accepted);
  auto stale = command(engine, "stale", "radio.set.mode", CommandClass::SafeReceiveSet, now);
  stale.writerLeaseId = lease->id;
  stale.agentGeneration = 0;
  stale.arguments["mode"] = "USB";
  assert(engine.execute(stale, now + 50).code == "STALE_AGENT_GENERATION");

  auto receiverA = command(engine, "receiver-a", "receiver.add", CommandClass::AgentRxRuntime, now);
  auto receiverB = command(engine, "receiver-b", "receiver.add", CommandClass::AgentRxRuntime, now);
  auto receiverC = command(engine, "receiver-c", "receiver.add", CommandClass::AgentRxRuntime, now);
  receiverB.agentGeneration = receiverA.agentGeneration = engine.state().agentGeneration;
  receiverB.expectedRadioGeneration = receiverA.expectedRadioGeneration = engine.state().radioGeneration;
  assert(engine.execute(receiverA, now + 60).accepted);
  receiverB.expectedRadioGeneration = engine.state().radioGeneration;
  assert(engine.execute(receiverB, now + 70).accepted);
  receiverC.agentGeneration = engine.state().agentGeneration;
  receiverC.expectedRadioGeneration = engine.state().radioGeneration;
  assert(engine.execute(receiverC, now + 80).code == "RECEIVER_LIMIT");

  const auto stopped = engine.globalStop(now + 90);
  assert(stopped.accepted && !engine.lease() && engine.state().receiverCount == 0);
  assert(engine.state().scanner == "STOPPED" && engine.state().recording == "STOPPED");

  const auto lease2 = engine.acquireLease("station-fixture", "fake-kx3", "operator-session", "control-window-a", now + 100, 1'000, "expiry");
  assert(lease2);
  engine.expire(now + 1'101);
  assert(!engine.lease());

  for (int i = 0; i < 10'000; ++i) {
    auto read = command(engine, "stress-" + std::to_string(i), "measurement.marker", CommandClass::AgentRxRuntime, now + 2'000);
    read.agentGeneration = engine.state().agentGeneration;
    read.expectedRadioGeneration = engine.state().radioGeneration;
    const auto result = engine.execute(read, now + 2'001);
    assert(result.accepted);
  }
  std::cout << "safe-control: 10000 deterministic commands passed\n";
  return 0;
}
