// SPDX-License-Identifier: GPL-3.0-only
#include "rigweave/workflow_control.h"
#include <cassert>
#include <iostream>

using namespace rigweave::workflow_control;
Command command(const Engine &engine, std::string id, std::string domain, std::string action, Capability capabilities, std::uint64_t now) {
  Command value; value.requestId="request-"+id; value.idempotencyKey="idempotency-"+id; value.domain=std::move(domain); value.action=std::move(action); value.operatorSessionId="operator-session"; value.role=Role::Operator; value.capabilities=capabilities; value.contextGeneration=engine.context().contextGeneration; value.agentGeneration=engine.context().agentGeneration; value.expiresMs=now+5'000; value.reason="deterministic M6 test"; return value;
}
int main() {
  constexpr std::uint64_t now=1'000'000; Engine engine(true);
  const Capability all=Capability::TxOperator|Capability::RotatorOperator|Capability::ProviderAuthor|Capability::GroupsAuthor|Capability::N1mmOperator;
  assert(engine.protocol().major==1 && engine.protocol().minor==2 && engine.context().frequencyHz==14'074'000);
  const auto noRole=engine.execute([&]{auto v=command(engine,"observer1","digi","digi.tx",all,now);v.role=Role::Observer;return v;}(),now); assert(noRole.code=="OPERATOR_ROLE_REQUIRED");
  assert(engine.execute(command(engine,"tx-arm01","digi","arm.tx",all,now),now).code=="DEMO_TX_ACCEPTANCE_ARMED");
  const auto tx=engine.execute(command(engine,"tx-send1","digi","digi.tx",all,now),now); assert(tx.accepted && tx.readback.at("rf")=="false");
  assert(engine.execute(command(engine,"rot-arm1","rotator","arm.rotator",all,now),now).accepted);
  auto move=command(engine,"rot-move","rotator","rotator.move",all,now); move.arguments={{"azimuth","90"},{"elevation","10"}}; const auto moved=engine.execute(move,now); assert(moved.accepted && moved.readback.at("physicalMovement")=="false");
  const auto replay=engine.execute(move,now+1); assert(replay.code==moved.code); move.arguments["azimuth"]="180"; assert(engine.execute(move,now+2).code=="IDEMPOTENCY_CONFLICT");
  assert(engine.execute(command(engine,"provider","portable","provider.publish",all,now),now).readback.at("realMutation")=="false");
  assert(engine.execute(command(engine,"groups01","groups","groups.send",all,now),now).readback.at("realSend")=="false");
  const auto stopped=engine.globalStop(now); assert(stopped.accepted && engine.authority().txArmId.empty() && engine.authority().movementArmId.empty());
  const auto generation=engine.context().contextGeneration; engine.invalidate("frequency change"); assert(engine.context().contextGeneration==generation+1);
  Engine physical(false); const auto unavailable=physical.execute(command(physical,"physical","digi","arm.tx",all,now),now); assert(unavailable.code=="TX_UNAVAILABLE_PHYSICAL_ACCEPTANCE_REQUIRED");
  std::cout<<"workflow-control: protocol 1.2 deterministic safety passed\n"; return 0;
}
