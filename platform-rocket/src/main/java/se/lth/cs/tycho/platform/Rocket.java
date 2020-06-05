package se.lth.cs.tycho.platform;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.RocketBackendPhase;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.phase.RemoveUnusedEntityDeclsPhase;

import java.util.List;

public class Rocket implements Platform {
	@Override
	public String name() {
		return "platform-rocket";
	}

	@Override
	public String description() {
		return "A backend for sequential C code and Chisel for Rocket-Chip.";
	}

	private static final List<Phase> phases = ImmutableList.<Phase> builder()
			.addAll(Compiler.frontendPhases())
			.addAll(Compiler.networkElaborationPhases())
			.addAll(Compiler.nameAndTypeAnalysis())
			.addAll(Compiler.actorMachinePhases())
			.add(new RemoveUnusedEntityDeclsPhase())
			.add(new RocketBackendPhase())
			.build();

	@Override
	public List<Phase> phases() {
		return phases;
	}
}
