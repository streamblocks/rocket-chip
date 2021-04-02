package se.lth.cs.tycho.backend.c;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

import static org.multij.BindingKind.LAZY;

@Module
public interface Main {
	@Binding(BindingKind.INJECTED)
	Backend backend();

	final String ACC_ANNOTATION = "acc";
	final boolean DONT_USE_FPU = false;
	final int ICACHE_SETS = 64; // sets * 64 gives the actual size of the cache
	final int DCACHE_SETS = 2048; // 2048 * 64 = 131072 ==> 128KB data memory

	default Emitter emitter() {
		return backend().emitter();
	}

	default Channels channels() {
		return backend().channels();
	}

	default MainNetwork mainNetwork() {
		return backend().mainNetwork();
	}

	default void generateCode() {
		global();
		fifo();
		actors();
		config();
		main();
	}

	default void global() {
		emitter().open(target().resolve("global.h"));
		backend().global().generateGlobalHeader();
		emitter().close();
		emitter().open(target().resolve("global.c"));
		backend().global().generateGlobalCode();
		emitter().close();
	}

	default void fifo() {
		emitter().open(target().resolve("fifo.h"));
		channels().fifo_h();
		emitter().close();
	}

	default void main() {
		Path mainTarget = target().resolve("main.c");
		emitter().open(mainTarget);
		CompilationTask task = backend().task();
		includeSystem("stdlib.h");
		includeSystem("stdio.h");
		includeSystem("stdbool.h");
		includeSystem("stdint.h");
		includeSystem("signal.h");
		includeSystem("string.h");
		includeUser("fifo.h");
		includeUser("prelude.h");
		includeUser("global.h");
		includeUser("../common/util.h");
		//include();
		for (String fileNameBase : actorFileNames()) {
			includeUser(fileNameBase + ".h");
		}

		includeSynchronization();
		channels().inputActorCode();
		channels().outputActorCode();
		mainNetwork().main(task.getNetwork());
		emitter().close();
	}

	default void actors() {
		backend().task().getNetwork().getInstances().forEach(this::actor);
	}

	@Binding(LAZY)
	default Set<String> actorFileNames() {
		return new LinkedHashSet<>();
	}

	default String actorFileName(String base) {
		base = "actor_" + base;
		String name = base;
		int i = 1;
		while (actorFileNames().contains(name)) {
			name = base + "_" + i;
		}
		actorFileNames().add(name);
		return name;
	}

	default void actor(Instance instance) {
		backend().instance().set(instance);
		GlobalEntityDecl actor = backend().task().getSourceUnits().stream()
				.map(SourceUnit::getTree)
				.filter(ns -> ns.getQID().equals(instance.getEntityName().getButLast()))
				.flatMap(ns -> ns.getEntityDecls().stream())
				.filter(decl -> decl.getName().equals(instance.getEntityName().getLast().toString()))
				.findFirst().get();
		String fileNameBase = actorFileName(instance.getInstanceName());
		String headerFileName = fileNameBase + ".h";
		emitter().open(target().resolve(headerFileName));
		String headerGuard = headerGuard(headerFileName);
		emitter().emit("#ifndef %s", headerGuard);
		emitter().emit("#define %s", headerGuard);
		emitDefaultHeaders();
		backend().structure().actorHdr(actor);
		emitter().emit("#endif");
		emitter().close();

		emitter().open(target().resolve(fileNameBase + ".c"));
		emitDefaultHeaders();
		includeUser("fifo.h");
		includeUser("global.h");
		includeUser(headerFileName);
		backend().structure().actorDecl(actor);
		emitter().close();
		backend().instance().clear();
	}

	default String headerGuard(String fileName) {
		return fileName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
	}

	default void emitDefaultHeaders() {
		includeSystem("stdint.h");
		includeSystem("stdbool.h");
		includeSystem("stdlib.h");
		includeSystem("string.h");
		emitter().emit("#pragma clang diagnostic ignored \"-Wparentheses-equality\"");
	}
	default void includeSystem(String h) { emitter().emit("#include <%s>", h); }
	default void includeUser(String h) { emitter().emit("#include \"%s\"", h); }

	default Path target() {
		return backend().context().getConfiguration().get(Compiler.targetPath);
	}

	default void include() {
		try (InputStream in = ClassLoader.getSystemResourceAsStream("c_backend_code/included.c")) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			reader.lines().forEach(emitter()::emitRawLine);
		} catch (IOException e) {
			throw CompilationException.from(e);
		}
	}

	default void includeSynchronization(){
		int numActors = backend().task().getNetwork().getInstances().size();
		emitter().emit("");
		emitter().emit("#define CORENUM " + numActors);
		emitter().emit("static volatile uint32_t syncVector[CORENUM] = ");
		String initVector = "{";
		for(int i = 0; i < numActors; i++) {
			if(i == numActors - 1)
				initVector += "0";
			else
				initVector += "0,";
		}
		initVector += "};";
		emitter().emit("\t" + initVector);

		emitter().emit("volatile int syncSum = 0;");
		for(int i = 1; i < numActors; i++){
			emitter().emit("volatile int fire" + i + " SECTION(\".core" + i + ".data\");");
		}

		emitter().emit("");
		emitter().emit("void syncCores(int cid, int nc){");
		emitter().increaseIndentation();
		emitter().emit("syncVector[cid] = 1;");
		emitter().emit("");
		emitter().emit("switch(cid){");
		emitter().emit("\tcase 0:");
		emitter().emit("\t\tfor(int i = 0; i < CORENUM; i++)");
		emitter().emit("\t\t\twhile(!syncVector[i]);");
		emitter().emit("");
		emitter().emit("\t\tfor(int i = 0; i < CORENUM; i++)");
		emitter().emit("\t\t\tsyncVector[i] = 0;");
		emitter().emit("");
		emitter().emit("\t\t// Fire the cores");
		for(int i = 1; i < numActors; i++)
			emitter().emit("\t\tfire" + i + " = 1;");
		emitter().emit("\t\tbreak;");

		for(int i = 1; i < numActors; i++) {
			emitter().emit("\tcase " + i + ":");
			emitter().emit("\t\twhile (fire" + i + " == 0);    // wait for the fire signal");
			emitter().emit("\t\tfire" + i + " = 0;            // reset the signal");
			emitter().emit("\t\tbreak;");
		}
		emitter().emit("");
		emitter().emit("\tdefault:");
		emitter().emit("\t\tbreak;");
		emitter().emit("}");

		emitter().decreaseIndentation();
		emitter().emit("}");
		emitter().emit("");
	}

	default void config(){
		// Subsystem/config
		Path mainTarget = target().resolve("subSystemConfig.scala");
		emitter().open(mainTarget);
		emitter().emit("//The following class should be copied into subsystem/config.scala");
		emitter().emit("");
		int actorNum = backend().task().getNetwork().getInstances().size();
		boolean hasAcc = false;
		for(Instance instance : backend().task().getNetwork().getInstances()) {
			GlobalEntityDecl actor = backend().task().getSourceUnits().stream()
					.map(SourceUnit::getTree)
					.filter(ns -> ns.getQID().equals(instance.getEntityName().getButLast()))
					.flatMap(ns -> ns.getEntityDecls().stream())
					.filter(decl -> decl.getName().equals(instance.getEntityName().getLast().toString()))
					.findFirst().get();
			ActorMachine actorMachine = (ActorMachine) actor.getEntity();
			for (Transition transition : actorMachine.getTransitions()) {
				Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
				hasAcc |= Annotation.hasAnnotationWithName(ACC_ANNOTATION, transition.getAnnotations());
			}
		}
		String acc = hasAcc ? "WithAcc" : "";

		emitter().emit("class With" + actorNum + "Core" + acc + "GeneratedBySB extends Config((site, here, up) => {");
		emitter().emit("case RocketTilesKey => {");

		backend().task().getNetwork().getInstances().forEach(this::coreConfigs);

		String coreList = "List(core0";

		for(int i = 1; i < actorNum; i++)
			coreList += ", core" + i;
		coreList += ")";

		emitter().emit("val coreList = " + coreList);
		emitter().emit("coreList");
		emitter().emit("}");
		emitter().emit("");
		emitter().emit("case RocketCrossingKey => List(RocketCrossingParams(");
		emitter().increaseIndentation();
		emitter().emit("crossingType = SynchronousCrossing(),");
		emitter().emit("master = TileMasterPortParams(cork = Some(true))");
		emitter().decreaseIndentation();
		emitter().emit("))");
		emitter().decreaseIndentation();
		emitter().emit("})");
		emitter().close();

		// System/config
		mainTarget = target().resolve("systemConfig.scala");
		emitter().open(mainTarget);
		emitter().emit("//The following class should be copied into system/config.scala");
		emitter().emit("");



		emitter().emit("class Tiny" + actorNum + "CoreSBGenConfig" + acc + " extends Config(");
		emitter().increaseIndentation();
		emitter().emit("new WithNMemoryChannels(0) ++");
		emitter().emit("new WithIncoherentTiles ++");
		emitter().emit("new With" + actorNum + "Core" + acc + "GeneratedBySB ++");
		emitter().emit("new BaseConfig)");
		emitter().close();
	}


	default void coreConfigs(Instance instance){
		GlobalEntityDecl actor = backend().task().getSourceUnits().stream()
				.map(SourceUnit::getTree)
				.filter(ns -> ns.getQID().equals(instance.getEntityName().getButLast()))
				.flatMap(ns -> ns.getEntityDecls().stream())
				.filter(decl -> decl.getName().equals(instance.getEntityName().getLast().toString()))
				.findFirst().get();
		ActorMachine actorMachine = (ActorMachine) actor.getEntity();

		List<Instance> instances =  backend().task().getNetwork().getInstances();
		ArrayList<String> actorNames = new ArrayList<String>();
		for(Instance x : instances)
			actorNames.add(x.getInstanceName());

		if(actorNames.indexOf(instance.getInstanceName()) == 0)
			emitter().increaseIndentation();

		emitter().emit("val core" + actorNames.indexOf(instance.getInstanceName()) + " = RocketTileParams(");
		emitter().increaseIndentation();
		emitter().emit("core = RocketCoreParams(");
		emitter().increaseIndentation();
		emitter().emit("useVM = false,");
		if(DONT_USE_FPU)
			emitter().emit("fpu = None,");
		emitter().emit("mulDiv = Some(MulDivParams(mulUnroll = 8))),");
		emitter().decreaseIndentation();
		emitter().emit("btb = None,");
		emitter().emit("icache = Some(ICacheParams(");
		emitter().increaseIndentation();
		emitter().emit("rowBits = site(SystemBusKey).beatBits,");
		emitter().emit("nSets = " + ICACHE_SETS + ",");
		emitter().emit("nWays = 1,");
		emitter().emit("nTLBEntries = 4,");
		emitter().emit("blockBytes = site(CacheBlockBytes))),");
		emitter().decreaseIndentation();
		emitter().emit("dcache = Some(DCacheParams(");
		emitter().increaseIndentation();
		emitter().emit("rowBits = site(SystemBusKey).beatBits,");
		emitter().emit("nSets = " + DCACHE_SETS + ",");
		emitter().emit("nWays = 1,");
		emitter().emit("nTLBEntries = 4,");
		emitter().emit("nMSHRs = 0,");
		emitter().emit("blockBytes = site(CacheBlockBytes),");
		emitter().emit("scratch = Some(0x80000000L + (" + actorNames.indexOf(instance.getInstanceName()) + " << (log2Up(" + DCACHE_SETS + ") + 6))))),");
		emitter().decreaseIndentation();

		int numAcc = 0;
		for (Transition transition : actorMachine.getTransitions()) {
			Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
			if(Annotation.hasAnnotationWithName(ACC_ANNOTATION, transition.getAnnotations())){
				if(numAcc > 3)
					break;

				String instanceName = instance.getInstanceName();

				emitter().emit("rocc = Seq(RoCCParams(");
				emitter().increaseIndentation();
				emitter().emit("opcodes = OpcodeSet.custom" + numAcc + ",");
				emitter().emit("generator = (p: Parameters) => {");
				emitter().increaseIndentation();
				emitter().emit("val " + instanceName + "_acc_" + numAcc + " = LazyModule(new Generated_" + instanceName + "_acc()(p))" );
				emitter().emit(instanceName + "_acc_" + numAcc + "})");
				emitter().decreaseIndentation();
				emitter().decreaseIndentation();
				emitter().emit("),");

				numAcc++;
			}
		}

		emitter().emit("hartId = " + actorNames.indexOf(instance.getInstanceName()));

		emitter().decreaseIndentation();
		emitter().emit(")");
		emitter().emit("");
	}
}
