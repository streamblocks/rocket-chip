package se.lth.cs.tycho.backend.c;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.stmt.StmtConsume;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.type.*;

import java.util.*;

@Module
public interface Structure {
    @Binding(BindingKind.INJECTED)
    Backend backend();

    final String ACC_ANNOTATION = "acc";

    default Emitter emitter() {
        return backend().emitter();
    }

    default Code code() {
        return backend().code();
    }

    default Types types() {
        return backend().types();
    }

    default DefaultValues defVal() {
        return backend().defaultValues();
    }

    default void actorHdr(GlobalEntityDecl decl) {
        String name = backend().instance().get().getInstanceName();
        actorHeader(name, decl.getEntity());
    }

    default void actorDecl(GlobalEntityDecl decl) {
        String name = backend().instance().get().getInstanceName();
        actor(name, decl.getEntity());
    }

    default void actorHeader(String name, Entity entity) {
    }

    default void actorHeader(String name, ActorMachine actorMachine) {
        actorMachineState(name, actorMachine);
        actorMachineInitHeader(name, actorMachine);
        actorMachineFreeHeader(name, actorMachine);
        actorMachineSectionMacro();
        actorMachineControllerHeader(name, actorMachine);
        actorMachineCustomInstructionMacros(name, actorMachine);
    }

    default void actor(String name, Entity entity) {
    }

    default void actor(String name, ActorMachine actorMachine) {
        actorMachineStateInit(name, actorMachine);
        actorMachineStateFree(name, actorMachine);
        actorMachineInit(name, actorMachine);
        actorMachineFree(name, actorMachine);
        // Print the class for subsystem/config.scala file
        //printSubSystemConfigCores();
        actorMachineTransitions(name, actorMachine);
        actorMachineConditions(name, actorMachine);
        actorMachineController(name, actorMachine);
    }

    default void actorMachineControllerHeader(String name, ActorMachine actorMachine) {
        backend().controllers().emitControllerHeader(name, actorMachine);
        emitter().emit("");
    }

    default void actorMachineController(String name, ActorMachine actorMachine) {
        backend().controllers().emitController(name, actorMachine);
        emitter().emit("");
        emitter().emit("");
    }

    default void actorMachineInitHeader(String name, ActorMachine actorMachine) {
        String selfParameter = name + "_state *self";
        List<String> parameters = getEntityInitParameters(selfParameter, actorMachine);
        emitter().emit("void %s_init_actor(%s);", name, String.join(", ", parameters));
        emitter().emit("");
    }

    default void actorMachineFreeHeader(String name, ActorMachine actorMachine) {
        String selfParameter = name + "_state *self";
        emitter().emit("void %s_free_actor(%s);", name, selfParameter);
        emitter().emit("");
    }

    default void actorMachineInit(String name, ActorMachine actorMachine) {
        String selfParameter = name + "_state *self";
        List<String> parameters = getEntityInitParameters(selfParameter, actorMachine);
        emitter().emit("void %s_init_actor(%s) {", name, String.join(", ", parameters));
        emitter().increaseIndentation();
        emitter().emit("self->program_counter = 0;");
        emitter().emit("");

        emitter().emit("// parameters");
        actorMachine.getValueParameters().forEach(d -> {
            emitter().emit("self->%s = %1$s;", backend().variables().declarationName(d));
        });
        emitter().emit("");

        emitter().emit("// input ports");
        actorMachine.getInputPorts().forEach(p -> {
            emitter().emit("self->%s_channel = %1$s_channel;", p.getName());
            emitter().emit("self->%s_channel_mirror = %1$s_channel_mirror;", p.getName());
        });
        emitter().emit("");

        emitter().emit("// output ports");
        actorMachine.getOutputPorts().forEach(p -> {
            emitter().emit("self->%s_channels = %1$s_channels;", p.getName());
            emitter().emit("self->%s_channels_mirror = %1$s_channels_mirror;", p.getName());
        });
        emitter().emit("");

        emitter().emit("// init persistent scopes");
        int i = 0;
        for (Scope s : actorMachine.getScopes()) {
            if (s.isPersistent()) {
                emitter().emit("%s_init_scope_%d(self);", name, i);
            }
            i = i + 1;
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
        emitter().emit("");
    }

    default void actorMachineFree(String name, ActorMachine actorMachine) {
        String selfParameter = name + "_state *self";
        emitter().emit("void %s_free_actor(%s) {", name, selfParameter);
        emitter().increaseIndentation();
        emitter().emit("// free persistent scopes");
        int i = 0;
        for (Scope s : actorMachine.getScopes()) {
            if (s.isPersistent()) {
                emitter().emit("%s_free_scope_%d(self);", name, i);
            }
            i = i + 1;
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
        emitter().emit("");
    }

    default void actorMachineCustomInstructionMacros(String name, ActorMachine actorMachine){
        emitter().emit("// Macros for inlinig code to call the accelerator ");
        emitter().emit("#define STR1(x) #x");
        emitter().emit("#define STR(x) STR1(x)");
        emitter().emit("#define EXTRACT(a, size, offset) (((~(~0 << size) << offset) & a) >> offset)");
        emitter().emit("#define CUSTOMX_OPCODE(x) CUSTOM_ ## x");
        emitter().emit("#define CUSTOM_0 0b0001011");
        emitter().emit("#define CUSTOM_1 0b0101011");
        emitter().emit("#define CUSTOM_2 0b1011011");
        emitter().emit("#define CUSTOM_3 0b1111011");
        emitter().emit("#define CUSTOMX(X, rd, rs1, rs2, funct)         \\");
        emitter().emit("\tCUSTOMX_OPCODE(X)                   |         \\");
        emitter().emit("\t(rd                   << (7))       |         \\");
        emitter().emit("\t(0x7                  << (7+5))     |         \\");
        emitter().emit("\t(rs1                  << (7+5+3))   |         \\");
        emitter().emit("\t(rs2                  << (7+5+3+5)) |         \\");
        emitter().emit("\t(EXTRACT(funct, 7, 0) << (7+5+3+5+5))");
        emitter().emit("");
        emitter().emit("// Standard macro that passes rd, rs1, and rs2 via registers");
        emitter().emit("#define ROCC_INSTRUCTION(X, rd, rs1, rs2, funct)                \\");
        emitter().emit("\tROCC_INSTRUCTION_R_R_R(X, rd, rs1, rs2, funct, 10, 11, 12)");
        emitter().emit("");
        emitter().emit("// rd, rs1, and rs2 are data");
        emitter().emit("// rd_n, rs_1, and rs2_n are the register numbers to use");
        emitter().emit("#define ROCC_INSTRUCTION_R_R_R(X, rd, rs1, rs2, funct, rd_n, rs1_n, rs2_n) { \\");
        emitter().emit("\tregister uint64_t rd_  asm (\"x\" # rd_n);                            \\");
        emitter().emit("\tregister uint64_t rs1_ asm (\"x\" # rs1_n) = (uint64_t) rs1;          \\");
        emitter().emit("\tregister uint64_t rs2_ asm (\"x\" # rs2_n) = (uint64_t) rs2;          \\");
        emitter().emit("\tasm volatile (                                                      \\");
        emitter().emit("\t\t\".word \" STR(CUSTOMX(X, rd_n, rs1_n, rs2_n, funct)) \"\\n\\t\"        \\");
        emitter().emit("\t\t: \"=r\" (rd_)                                                      \\");
        emitter().emit("\t\t: [_rs1] \"r\" (rs1_), [_rs2] \"r\" (rs2_));                          \\");
        emitter().emit("\trd = rd_;                                                           \\");
        emitter().emit("}");
        emitter().emit("");
        emitter().emit("#define XCUSTOM_ACC 0");
        emitter().emit("#define FUNCT_FIRE  4");
        emitter().emit("#define FUNCT_IN1   1");
        emitter().emit("#define FUNCT_IN2   2");
        emitter().emit("#define FUNCT_IN3   3");
        emitter().emit("#define FUNCT_READ  5");
        emitter().emit("");
        emitter().emit("// Standard macro that passes rd, rs1, and rs2 via registers and expects does not block (while waiting for the result)");
        emitter().emit("#define ROCC_INSTRUCTION_NO_BLOCK(X, rd, rs1, rs2, funct)                \\");
        emitter().emit("\tROCC_INSTRUCTION_NO_BLOCK_R_R_R(X, rd, rs1, rs2, funct, 10, 11, 12)");
        emitter().emit("");
        emitter().emit("// rd, rs1, and rs2 are data");
        emitter().emit("// rd_n, rs_1, and rs2_n are the register numbers to use");
        emitter().emit("#define ROCC_INSTRUCTION_NO_BLOCK_R_R_R(X, rd, rs1, rs2, funct, rd_n, rs1_n, rs2_n) { \\");
        emitter().emit("\tregister uint64_t rd_  asm (\"x\" # rd_n);                            \\");
        emitter().emit("\tregister uint64_t rs1_ asm (\"x\" # rs1_n) = (uint64_t) rs1;          \\");
        emitter().emit("\tregister uint64_t rs2_ asm (\"x\" # rs2_n) = (uint64_t) rs2;          \\");
        emitter().emit("\tasm volatile (                                                      \\");
        emitter().emit("\t\t\".word \" STR(CUSTOMX_NP(X, rd_n, rs1_n, rs2_n, funct)) \"\\n\\t\"     \\");
        emitter().emit("\t\t: \"=r\" (rd_)                                                      \\");
        emitter().emit("\t\t: [_rs1] \"r\" (rs1_), [_rs2] \"r\" (rs2_));                          \\");
        emitter().emit("\trd = rd_;                                                           \\");
        emitter().emit("}");
        emitter().emit("");
        emitter().emit("// no requirement for return register");
        emitter().emit("#define CUSTOMX_NP(X, rd, rs1, rs2, funct)         \\");
        emitter().emit("\tCUSTOMX_OPCODE(X)                   |         \\");
        emitter().emit("\t(rd                   << (7))       |         \\");
        emitter().emit("\t(0x3                  << (7+5))     |         \\");
        emitter().emit("\t(rs1                  << (7+5+3))   |         \\");
        emitter().emit("\t(rs2                  << (7+5+3+5)) |         \\");
        emitter().emit("\t(EXTRACT(funct, 7, 0) << (7+5+3+5+5))");
        emitter().emit("");
        emitter().emit("#define ROCC_CUBIC(rd, rs1, rs2, rs3, rs4, rs5, rs6, rs7, rs8)                \\");
        emitter().emit("\tROCC_CUBIC_R(rd, rs1, rs2, rs3, rs4, rs5, rs6, rs7, rs8, 4, 5, 6, 7, 8, 9, 10, 11, 12)");
        emitter().emit("");
        emitter().emit("#define ROCC_CUBIC_R(rd, rs1, rs2, rs3, rs4, rs5, rs6, rs7, rs8, rd_n, rs1_n, rs2_n, rs3_n, rs4_n, rs5_n, rs6_n, rs7_n, rs8_n) { \\");
        emitter().emit("\tregister uint64_t rd_  asm (\"x\" # rd_n);                            	\\");
        emitter().emit("\tregister uint64_t rs1_ asm (\"x\" # rs1_n) = (uint64_t) rs1;          	\\");
        emitter().emit("\tregister uint64_t rs2_ asm (\"x\" # rs2_n) = (uint64_t) rs2;          	\\");
        emitter().emit("\tregister uint64_t rs3_ asm (\"x\" # rs3_n) = (uint64_t) rs3;          	\\");
        emitter().emit("\tregister uint64_t rs4_ asm (\"x\" # rs4_n) = (uint64_t) rs4;          	\\");
        emitter().emit("\tregister uint64_t rs5_ asm (\"x\" # rs5_n) = (uint64_t) rs5;          	\\");
        emitter().emit("\tregister uint64_t rs6_ asm (\"x\" # rs6_n) = (uint64_t) rs6;          	\\");
        emitter().emit("\tregister uint64_t rs7_ asm (\"x\" # rs7_n) = (uint64_t) rs7;          	\\");
        emitter().emit("\tregister uint64_t rs8_ asm (\"x\" # rs8_n) = (uint64_t) rs8;          	\\");
        emitter().emit("\tasm volatile (                                                     	  \\");
        emitter().emit("\t\t\".word \" STR(CUSTOMX_NP(0, rd_n, rs1_n, rs2_n, FUNCT_IN1)) \"\\n\\t\"   \\");
        emitter().emit("\t\t\".word \" STR(CUSTOMX_NP(0, rd_n, rs3_n, rs4_n, FUNCT_IN2)) \"\\n\\t\"   \\");
        emitter().emit("\t\t\".word \" STR(CUSTOMX_NP(0, rd_n, rs5_n, rs6_n, FUNCT_IN3)) \"\\n\\t\"   \\");
        emitter().emit("\t\t\".word \" STR(CUSTOMX(0, rd_n, rs7_n, rs8_n, FUNCT_FIRE)) \"\\n\\t\"     \\");
        emitter().emit("\t\t: \"=r\" (rd_)                                                    	  \\");
        emitter().emit("\t\t: [_rs1] \"r\" (rs1_), [_rs2] \"r\" (rs2_), [_rs3] \"r\" (rs3_), 			    \\");
        emitter().emit("\t\t[_rs4] \"r\" (rs4_), [_rs5] \"r\" (rs5_), [_rs6] \"r\" (rs6_), [_rs7] 	  \\");
        emitter().emit("\t\t\"r\" (rs7_), [_rs8] \"r\" (rs8_)) ;   			                            \\");
        emitter().emit("\t\trd = rd_;                                                          	\\");
        emitter().emit("}");
    }

    default void actorMachineSectionMacro(){
        emitter().emit("#undef  SECTION");
        emitter().emit("#define SECTION(x) __attribute__((section(x)))");
    }

    default List<String> getEntityInitParameters(String selfParameter, Entity actorMachine) {
        List<String> parameters = new ArrayList<>();
        parameters.add(selfParameter);
        actorMachine.getValueParameters().forEach(d -> {
            parameters.add(code().declaration(types().declaredType(d), backend().variables().declarationName(d)));
        });
        actorMachine.getInputPorts().forEach(p -> {
            String type = backend().channels().targetEndTypeSize(new Connection.End(Optional.of(backend().instance().get().getInstanceName()), p.getName()));
            parameters.add(String.format("channel_%s *%s_channel", type, p.getName()));
            parameters.add(String.format("channel_%s_mirror *%s_channel_mirror", type, p.getName()));
        });
        actorMachine.getOutputPorts().forEach(p -> {
            Connection.End source = new Connection.End(Optional.of(backend().instance().get().getInstanceName()), p.getName());
            String type = backend().channels().sourceEndTypeSize(source);
            parameters.add(String.format("channel_list_%s %s_channels", type, p.getName()));
            parameters.add(String.format("channel_list_%s_mirror %s_channels_mirror", type, p.getName()));
        });
        return parameters;
    }

    default void prepareCustomInstruction(Transition transition){
        emitter().emit("// Prepare the custom instruction");
        Set<VarDecl> inputVars = backend().ioVariables().IOVariablesRead(transition);
        Set<VarDecl> outputVars = backend().ioVariables().IOVariablesWrite(transition);

        emitter().emit("typedef union{");
        emitter().increaseIndentation();
        emitter().emit("uint64_t accIO64;");
        emitter().emit("float accIO32[2];");
        emitter().decreaseIndentation();
        emitter().emit("} accIO_t;");
        emitter().emit("");
        for(int j = 0; j < (inputVars.size() + 1) / 2; j++)
            emitter().emit("accIO_t accInput" + j + ";"); // Create 64 bit inputs to be used in the instruction

        for(int j = 0; j < (outputVars.size() + 1) / 2; j++)
            emitter().emit("accIO_t accOutput" + j + ";"); // Create 64 bit outputs

        if(outputVars.size() == 0)
            emitter().emit("accIO_t accOutput0;");

        //Copy the inputs into the 64 bit variable
        int m = 0;
        for(VarDecl decl : inputVars){
            String varFullName = "";
            IRNode parent = backend().tree().parent(decl);

            if (parent instanceof Scope) {
                varFullName = "self->a_" + decl.getName().replace("_", "__");
            } else if (parent instanceof ActorMachine) {
                varFullName = decl.getName() + "AM";
            } else if (parent instanceof NamespaceDecl) {
                varFullName = "g_test_" + decl.getName();
            } else {
                if(decl.getValue() instanceof ExprInput){
                    varFullName = code().evaluate(decl.getValue());
                } else
                    varFullName = decl.getName();
            }

            if(m % 2 == 0)
                emitter().emit("accInput" + m/2 + ".accIO32[1] = " + varFullName + ";");
            else
                emitter().emit("accInput" + m/2 + ".accIO32[0] = " + varFullName + ";");
            m++;
        }
        emitter().emit("");
        emitter().emit("// Call the custom instruction(s)");

        // Call the custom instructions
        m = 0;
        for(int j = 0; j < (inputVars.size() + 3) / 4; j++){
            if (j + 1 < (inputVars.size() + 3) / 4)
                emitter().emit("ROCC_INSTRUCTION_NO_BLOCK(XCUSTOM_ACC, accOutput0.accIO64, accInput" + m + ".accIO64, accInput" + (m+1) + ".accIO64, FUNCT_IN1);");
            else {
                String tmp = "";
                if(m + 2 > (inputVars.size() + 1) / 2) // m starts from 0
                    tmp = "0";
                else
                    tmp = "accInput" + (m + 1) + ".accIO64";
                emitter().emit("ROCC_INSTRUCTION(XCUSTOM_ACC, accOutput0.accIO64, accInput" + m + ".accIO64, " + tmp + ", FUNCT_FIRE);");
            }

            m = m + 2;
        }

        // Read back the result(s)
        for(int j = 1 ; j < (outputVars.size() + 1) / 2; j++){
            emitter().emit("ROCC_INSTRUCTION(XCUSTOM_ACC, accOutput" + j + ".accIO64, 0, 0, FUNCT_READ);");
        }
        // Copy the returned results into the local variables
        m = 0;
        for(VarDecl decl : outputVars){
            String varFullName = "";
            IRNode parent = backend().tree().parent(decl);

            if (parent instanceof Scope) {
                varFullName = "self->a_" + decl.getName().replace("_", "__");
            } else if (parent instanceof ActorMachine) {
                varFullName = decl.getName() + "AM";
            } else if (parent instanceof NamespaceDecl) {
                varFullName = "g_test_" + decl.getName();
            } else {
                varFullName = "l_" + decl.getName();
                emitter().emit(code().declaration(new IntType(OptionalInt.of(32), true), varFullName));
                //TODO use variable's type instead of constant UInt
            }

            emitter().emit("");
            if(m % 2 == 0)
                emitter().emit(varFullName + " = accOutput" + m/2 + ".accIO32[1];");
            else
                emitter().emit(varFullName + " = accOutput" + m/2 + ".accIO32[0];");
            m++;
        }

        // Write the outputs & Consume the input
        for(IRNode child : transition.getBody()){
            if(child instanceof StmtBlock)
                for(IRNode stmt : ((StmtBlock)child).getStatements()) {
                    if (stmt instanceof StmtWrite)
                        code().execute((StmtWrite) stmt);
                    else if (stmt instanceof StmtConsume)
                        code().execute((StmtConsume) stmt);
                }
        }
    }

    default void actorMachineTransitions(String name, ActorMachine actorMachine) {
        int i = 0;
        for (Transition transition : actorMachine.getTransitions()) {
            Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
            if (annotation.isPresent()) {
                String actionTag = ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
                emitter().emit("// -- Action Tag : %s", actionTag);
            }
            emitter().emit("/*static*/ void %s_transition_%d(%s_state *self) {", name, i, name);
            emitter().increaseIndentation();
            backend().trackable().enter();
            // -- Check if transition contains @acc annotation
            boolean acceleratedTransition = Annotation.hasAnnotationWithName(ACC_ANNOTATION, transition.getAnnotations());
            if (acceleratedTransition) {
                prepareCustomInstruction(transition);
                backend().codeChisel().acceleratedTransition(name, actorMachine, transition);
                // Print the accelerators for the class in subsystem/config.scala file
                //printSubSystemConfigAcc();
            } else {
                transition.getBody().forEach(code()::execute);
            }
            backend().trackable().exit();
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
            emitter().emit("");
            i++;
        }
    }

    default void actorMachineConditions(String name, ActorMachine actorMachine) {
        int i = 0;
        for (Condition condition : actorMachine.getConditions()) {
            emitter().emit("static _Bool %s_condition_%d(%s_state *self) {", name, i, name);
            emitter().increaseIndentation();
            backend().trackable().enter();
            String result = evaluateCondition(condition);
            String tmp = backend().variables().generateTemp();
            emitter().emit("%s = %s;", backend().code().declaration(BoolType.INSTANCE, tmp), result);
            backend().trackable().exit();
            emitter().emit("return %s;", tmp);
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
            emitter().emit("");
            i++;
        }
    }

    String evaluateCondition(Condition condition);

    default String evaluateCondition(PredicateCondition condition) {
        return code().evaluate(condition.getExpression());
    }

    default String evaluateCondition(PortCondition condition) {
        if (condition.isInputCondition()) {
            return String.format("channel_has_data_%s(self->%s_channel, %d)", code().inputPortTypeSize(condition.getPortName()), condition.getPortName().getName(), condition.N());
        } else {
            return String.format("channel_has_space_%s(self->%s_channels_mirror, %d)", code().outputPortTypeSize(condition.getPortName()), condition.getPortName().getName(), condition.N());
        }
    }


    default void actorMachineStateInit(String name, ActorMachine actorMachine) {
        int i = 0;
        for (Scope scope : actorMachine.getScopes()) {
            emitter().emit("static void %s_init_scope_%d(%s_state *self) {", name, i, name);
            emitter().increaseIndentation();
            for (VarDecl var : scope.getDeclarations()) {
                Type type = types().declaredType(var);
                if (var.isExternal() && type instanceof CallableType) {
                    String wrapperName = backend().callables().externalWrapperFunctionName(var);
                    String variableName = backend().variables().declarationName(var);
                    String t = backend().callables().mangle(type).encode();
                    emitter().emit("self->%s = (%s) { *%s, NULL };", variableName, t, wrapperName);
                } else if (var.getValue() != null) {
                    emitter().emit("{");
                    emitter().increaseIndentation();
                    backend().trackable().enter();
                    code().copy(types().declaredType(var), "self->" + backend().variables().declarationName(var), types().type(var.getValue()), code().evaluate(var.getValue()));
                    backend().trackable().exit();
                    emitter().decreaseIndentation();
                    emitter().emit("}");
                } else {
                    emitter().emit("{");
                    emitter().increaseIndentation();
                    String tmp = backend().variables().generateTemp();
                    emitter().emit("%s = %s;", code().declaration(type, tmp), backend().defaultValues().defaultValue(type));
                    emitter().emit("self->%s = %s;", backend().variables().declarationName(var), tmp);
                    emitter().decreaseIndentation();
                    emitter().emit("}");
                }
            }
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
            emitter().emit("");
            i++;
        }
    }

    default void actorMachineStateFree(String name, ActorMachine actorMachine) {
        int i = 0;
        for (Scope scope : actorMachine.getScopes()) {
            emitter().emit("static void %s_free_scope_%d(%s_state *self) {", name, i, name);
            emitter().increaseIndentation();
            for (VarDecl var : scope.getDeclarations()) {
                emitter().emit("{");
                emitter().increaseIndentation();
                backend().free().apply(types().declaredType(var), String.format("self->%s", backend().variables().declarationName(var)));
                emitter().decreaseIndentation();
                emitter().emit("}");
            }
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit("");
            emitter().emit("");
            i++;
        }
    }


    default void actorMachineState(String name, ActorMachine actorMachine) {
        emitter().emit("typedef struct {");
        emitter().increaseIndentation();

        emitter().emit("int program_counter;");
        emitter().emit("");

        emitter().emit("// parameters");
        for (VarDecl param : actorMachine.getValueParameters()) {
            String decl = code().declaration(types().declaredType(param), backend().variables().declarationName(param));
            emitter().emit("%s;", decl);
        }
        emitter().emit("");

        emitter().emit("// input ports");
        for (PortDecl input : actorMachine.getInputPorts()) {
            String type = backend().channels().targetEndTypeSize(new Connection.End(Optional.of(backend().instance().get().getInstanceName()), input.getName()));
            emitter().emit("channel_%s *%s_channel;", type, input.getName());
            emitter().emit("channel_%s_mirror *%s_channel_mirror;", type, input.getName());
        }
        emitter().emit("");

        emitter().emit("// output ports");
        for (PortDecl output : actorMachine.getOutputPorts()) {
            Connection.End source = new Connection.End(Optional.of(backend().instance().get().getInstanceName()), output.getName());
            String type = backend().channels().sourceEndTypeSize(source);
            emitter().emit("channel_list_%s %s_channels;", type, output.getName());
            emitter().emit("channel_list_%s_mirror %s_channels_mirror;", type, output.getName());
        }
        emitter().emit("");

        int i = 0;
        for (Scope scope : actorMachine.getScopes()) {
            emitter().emit("// scope %d", i);
            backend().callables().declareEnvironmentForCallablesInScope(scope);
            for (VarDecl var : scope.getDeclarations()) {
                String decl = code().declaration(types().declaredType(var), backend().variables().declarationName(var));
                emitter().emit("%s;", decl);
            }
            emitter().emit("");
            i++;
        }
        emitter().decreaseIndentation();
        emitter().emit("} %s_state;", name);
        emitter().emit("");
        emitter().emit("");
    }

    default void actorDecls(List<GlobalEntityDecl> entityDecls) {
        entityDecls.forEach(backend().structure()::actorDecl);
    }
}
