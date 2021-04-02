package se.lth.cs.tycho.backend.chisel;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.backend.c.Backend;
import se.lth.cs.tycho.backend.c.Emitter;
import se.lth.cs.tycho.backend.c.Trackable;
import se.lth.cs.tycho.backend.c.Variables;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.ir.stmt.lvalue.*;
import se.lth.cs.tycho.type.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.multij.BindingKind.LAZY;

@Module
public interface CodeChisel {
    @Binding(BindingKind.INJECTED)
    Backend backend();

    @Binding(LAZY)
    default SSAChisel ssaChisel() { return new SSAChisel(); }

    @Binding(LAZY)
    default PipelineChisel pipelineChisel() { return new PipelineChisel(); }

    default Path target() {
        return backend().context().getConfiguration().get(Compiler.targetPath);
    }

    @Binding(LAZY)
    default Emitter emitter() { return new Emitter(); }


    default Types types() {
        return backend().types();
    }

    default Variables variables() {
        return backend().variables();
    }

    /*
    TODO: This part should probably be a separate file.
    Generate arithmetic operation blocks
     */

    /*
        Arithmetic blocks generators
        Generate and print the block and return the block name
    */
    default String generateFloatAdder(){
        String adder = "fpAdd" + backend().uniqueNumbers().next();
        emitter().emit("val " + adder + " = Module(new FPAdd(32))");
        return adder;
    }
    default String generateAdderWithSize(int size){
        //TODO: Implement
        return "generateAdderWithSize is not implemented";
    }

    default String generateFloatSubtractor(){
        String subtractor = "fpSub" + backend().uniqueNumbers().next();
        emitter().emit("val " + subtractor + " = Module(new FPSub(32))");
        return subtractor;
    }

    default String generateFloatMultiplier(){
        String multiplier = "fpMul" + backend().uniqueNumbers().next();
        emitter().emit("val " + multiplier + " = Module(new FPMult(32))");
        return multiplier;
    }
    default String generateMultiplierWithSize(int size){
        //TODO: Implement
        return "generateMultiplierWithSize is not implemented";
    }

    default String generateFloatDivider(){
        String divider = "fpDiv" + backend().uniqueNumbers().next();
        emitter().emit("val " + divider + " = Module(new FPDiv(32))");
        return divider;
    }
    default String generateDividerWithSize(int size){
        //TODO: Implement
        return "generateDividerWithSize is not implemented";
    }

    default String generateFloatSquareRoot(){
        String sqrt = "fpSqrt" + backend().uniqueNumbers().next();
        emitter().emit("val " + sqrt + " = Module(new FPSqrt())");
        return sqrt;
    }

    /* End of arithmetic operation block generation */

    default void printDelayRegsOperator(String operator, Expression left, Expression right){
        pipelineChisel().setLocalDelay(0);
        String input1 = evaluate(left);
        int delay1 = pipelineChisel().getLocalDelay();
        pipelineChisel().setLocalDelay(0);
        String input2 = evaluate(right);
        int delay2 = pipelineChisel().getLocalDelay();

        if(delay1 != delay2){   //if the inputs have different delays, equalize them
            long uniqeNo = 0;
            uniqeNo = backend().uniqueNumbers().next();
            String quickerInput = (delay1 > delay2) ? input2 : input1;
            emitter().emit("val inputDelayReg" + uniqeNo + " = RegNext(" + quickerInput + ")");
            for(int i = 1; i < Math.abs(delay1-delay2); i++){
                uniqeNo = backend().uniqueNumbers().next();
                emitter().emit("val inputDelayReg" + uniqeNo + " = RegNext(inputDelayReg" + (uniqeNo-1) + ")");
            }
            emitter().emit(operator + ".io.in1 := " + ((delay1 > delay2) ? input1 : ("inputDelayReg" + uniqeNo)));
            emitter().emit(operator + ".io.in2 := " + ((delay2 > delay1) ? input2 : ("inputDelayReg" + uniqeNo)));
        }
        else {
            emitter().emit(operator + ".io.in1 := " + input1);
            emitter().emit(operator + ".io.in2 := " + input2);
        }
        pipelineChisel().setLocalDelay(Integer.max(delay1, delay2));
    }

    default void setUnaryOpDelay(String operator, Expression left){
        pipelineChisel().setLocalDelay(0);
        String input1 = evaluate(left);
        int delay1 = pipelineChisel().getLocalDelay();

        emitter().emit(operator + ".io.in := " + input1);
        pipelineChisel().setLocalDelay(delay1);
    }

    default void generateClassDefinition(String instanceName, int transitionIndex){
        emitter().emit("class " + instanceName + "_acc_" + transitionIndex + " extends Module");
    }

    default void generateIO(Set<VarDecl> inputVars, Set<VarDecl> outputVars){
        emitter().increaseIndentation();
        emitter().emit("val io = IO(new Bundle{");
        emitter().increaseIndentation();
        for (VarDecl decl : inputVars) {
            String inputName = decl.getName();
            //Type type = types().declaredType(decl); //TODO use the type to set the size of the input
            emitter().emit("val " + inputName + "_in = Input(UInt(width = 32.W))");
        }
        for (VarDecl decl : outputVars) {
            String outputName = decl.getName();
            //Type type = types().declaredType(decl); //TODO use the type to set the size of the output
            emitter().emit("val " + outputName + "_out = Output(UInt(width = 32.W))");
        }
        emitter().emit("val en    = Input(Bool())");
        emitter().emit("val valid = Output(Bool())");
        emitter().decreaseIndentation();
        emitter().emit("})");

        emitter().decreaseIndentation();
    }

    default void setInput(Set<VarDecl> inputVars){
        emitter().increaseIndentation();
        for (VarDecl decl : inputVars) {
            // If the input is the port variable, it will be set later (not here). Because there is already an assignment statement for it in the AM.
            IRNode parent = backend().tree().parent(decl);
            if (parent instanceof Scope || parent instanceof ActorMachine || parent instanceof NamespaceDecl) {
                String inputName = decl.getName();
                String declarationName = variables().declarationName(decl);
                emitter().emit("val " + declarationName + "_v0 = io." + inputName + "_in");
                ssaChisel().setVersion(declarationName, 0);
            }
        }
        emitter().decreaseIndentation();
    }

    default void setOutput(Set<VarDecl> outputVars){
        emitter().increaseIndentation();
        for (VarDecl decl : outputVars) {
            String outputName = decl.getName();
            String declarationName = variables().declarationName(decl);
            String version = "_v" + Integer.toString(ssaChisel().getVersion(declarationName));
            emitter().emit("io." + outputName + "_out := " + declarationName + version);
        }
        int longestDelay = pipelineChisel().getLongestDelay();
        if(longestDelay != 0) {
            emitter().emit("val validReg0 = RegNext(io.en)");
            for (int i = 0; i < longestDelay - 1; i++)
                emitter().emit("val validReg" + (i + 1) + " = RegNext(validReg" + i + ")");
            emitter().emit("io.valid := validReg" + (longestDelay - 1));
        }
        emitter().decreaseIndentation();
    }

    default void findVarVersions(IRNode node) {
        Set<LValue> lValues = ssaChisel().findLValues(node);

        lValues.forEach(child -> ssaChisel().increaseLocalVersion(lvalue(child)));
        lValues.forEach(child -> ssaChisel().setLocalVarType(lvalue(child), types().type(child)));
    }

    default void declareVersions(Set<VarDecl> inputVars){
        emitter().emit("//Declare all variable versions");
        HashMap<String, Integer> allVarVersions = ssaChisel().getLocalVarVersions();
        allVarVersions.keySet().forEach(varName -> printVarVersion(varName, inputVars));
    }

    default void printVarVersion(String varName, Set<VarDecl> inputVars){
        int numVer = ssaChisel().getLocalVersion(varName);
        Type varType = ssaChisel().getLocalType(varName);
        int start = 0;
        for(VarDecl decl : inputVars) {
            String declarationName = variables().declarationName(decl);
            if(declarationName.contains(varName)) // Don't print version 0
                start = 1;
        }
        // TODO versions 0 were not printed but they may be used in the muxes, therefore we print them now (QRD case study)
        for(int i = start ; i < numVer + 1; i++) {
            String d = declaration(varType, varName + "_v" + (i));
            emitter().emit("%s", d);
            emitter().emit(varName +  "_v" + i + " := 0.U");    // Initialize the variable
            if(varName.startsWith("a_"))
                ssaChisel().setVersion(varName, 0);
        }
    }

    default void printPhiFunctions (String condition, HashMap<String, Integer> versions1, HashMap<String, Integer> versions2/*old versions*/ ){
        for (String variable : versions1.keySet()){
            if (versions1.containsKey(variable)){
                if(versions1.get(variable) != versions2.get(variable)){
                    // TODO Check if the phi var is the latest version of the variable, otherwise declare the latest version but use the current
                    int latestVersion = ssaChisel().getLocalVersion(variable);
                    int currentVersion = ssaChisel().getPublicVarVersions().get(variable);
                    emitter().emit("val " + variable + "_v" + Integer.toString(latestVersion + 1) + " = Wire(UInt(width = 32.W))");
                    String newVarName =  variable + "_v" + Integer.toString(ssaChisel().getPublicVarVersions().get(variable) + 1);
                    String variable1 = variable + "_v" + Integer.toString(versions1.get(variable)); // new versions
                    String variable2 = variable + "_v" + Integer.toString(versions2.get(variable)); // old version

                    // if the older version is 0 and the variable is not an input, then it is not declared
//                    if(versions2.get(variable) == 0 && )
  //                      variable2 = "0";

                    int delay1 = pipelineChisel().getGlobalDelay(variable1);
                    int delay2 = pipelineChisel().getGlobalDelay(variable2);
                    if(delay1 != delay2){
                        long uniqeNo = 0;
                        uniqeNo = backend().uniqueNumbers().next();
                        String quickerInput = (delay1 > delay2) ? (variable2) : (variable1);
                        emitter().emit("val phiDelayReg" + uniqeNo + " = RegNext(" + quickerInput + ")");
                        for(int i = 1; i < Math.abs(delay1-delay2); i++){
                            uniqeNo = backend().uniqueNumbers().next();
                            emitter().emit("val phiDelayReg" + uniqeNo + " = RegNext(phiDelayReg" + (uniqeNo - 1) + ")");
                        }
                        emitter().emit(newVarName + " := Mux(" +
                                condition + ", " + ( (delay1 > delay2) ? variable1 : ("phiDelayReg" + uniqeNo) ) + ", " +
                                ((delay2 > delay1) ? variable2 : ("phiDelayReg" + uniqeNo) ) + ")");
                    }
                    else {
                        emitter().emit(newVarName + " = Mux(" +
                                condition + ", " + variable + "_v" + Integer.toString(versions1.get(variable)) +
                                ", " + variable + "_v" + Integer.toString(versions2.get(variable)) + ")");
                    }
                    ssaChisel().increaseVersion(variable);
                    pipelineChisel().setGlobalDelay(newVarName, (delay1 > delay2) ? delay1 : delay2);
                    emitter().emit("// " + newVarName + " delay: " + ((delay1 > delay2) ? delay1 : delay2) + " cycles");
                    emitter().emit("");
                }
            }
        }
    }

    default void printPackage(String name, int transitionIndex){
        emitter().emit("package " + name + "_acc_" + transitionIndex);
    }
    default void printImports(){
        emitter().emit("import chisel3._");
        emitter().emit("import chisel3.util._");
        emitter().emit("import FPPipelined._");
    }

    default void printAccInterfaceExtension(String instanceName, int transitionIndex, Set<VarDecl> inputVars, Set<VarDecl> outputVars){
        emitter().emit("/* RoCC Interface extension for the accelerator (" + instanceName + "_acc_" + transitionIndex + ") generated by StreamBlocks */");
        emitter().emit("");

        // Package
        emitter().emit("package freechips.rocketchip.tile");
        emitter().emit("");

        // Imports
        emitter().emit("import " + instanceName + "_acc_" + transitionIndex + "._");
        emitter().emit("import Chisel._");
        emitter().emit("import freechips.rocketchip.config._");
        emitter().emit("import freechips.rocketchip.subsystem._");
        emitter().emit("import freechips.rocketchip.diplomacy._");
        emitter().emit("import freechips.rocketchip.rocket._");
        emitter().emit("import freechips.rocketchip.tilelink._");
        emitter().emit("import freechips.rocketchip.util.InOrderArbiter");
        emitter().emit("");

        // Interface definition
        emitter().emit("class Generated_" + instanceName + "_acc (implicit p: Parameters) extends LazyRoCC() (p){" );
        emitter().increaseIndentation();
        emitter().emit("override lazy val module = new Generated_" + instanceName + "_acc_module(this)");
        emitter().decreaseIndentation();
        emitter().emit("}");

        emitter().emit("");
        // Interface implementation
        emitter().emit("class Generated_" +instanceName + "_acc_module(outer: Generated_" + instanceName + "_acc)(implicit p: Parameters)");
        emitter().emit("extends LazyRoCCModule(outer) with HasCoreParameters{");
        emitter().increaseIndentation();
        emitter().emit("");
        emitter().emit("// Instantiate the accelerator");
        emitter().emit("val acc = Module(new " + instanceName + "_acc_" + transitionIndex + "())");
        emitter().emit("");
        emitter().emit("// Read the instruction fields");
        emitter().emit("val cmd   = Queue(io.cmd)");
        emitter().emit("val funct = cmd.bits.inst.funct");
        emitter().emit("val fire  = cmd.valid && (funct === 4.U)");

        int numInputs = inputVars.size();
        for(int i = 1; i < (numInputs - 1) / 4; i++)
            emitter().emit("val in" + i + "   = cmd.valid && (funct === " + i + ".U)");

        // We need to know if there will be read calls to read the outputs which don't fit into the first instructions rd
        int numOutputs = outputVars.size();
        if(numOutputs > 2)
            emitter().emit("val read  = cmd.valid && (funct === 5.U)");
        for(int i = 1; i < (numOutputs - 1) / 2; i++)
            emitter().emit("val outReg" + (i - 1) + " = RegInit(UInt(0, 64.W))");

        emitter().emit("val returnReg = RegInit(cmd.bits.inst.rd)");
        emitter().emit("");

        // Check if we need to store any inputs
        ArrayList<VarDecl> inputVarsList = new ArrayList<VarDecl>(inputVars);
        ArrayList<VarDecl> outputVarsList = new ArrayList<VarDecl>(outputVars);
        for(int i = 0; i < (numInputs - 1) / 4; i++) {
            emitter().emit("val " + inputVarsList.get(i).getName() + "_in = RegInit(UInt(0, 32.W))");
            emitter().emit("val " + inputVarsList.get(i + 1).getName() + "_in = RegInit(UInt(0, 32.W))");
            emitter().emit("val " + inputVarsList.get(i + 2).getName() + "_in = RegInit(UInt(0, 32.W))");
            emitter().emit("val " + inputVarsList.get(i + 3).getName() + "_in = RegInit(UInt(0, 32.W))");
            emitter().emit("");

            emitter().emit("when(in" + (i + 1) + "){");
            emitter().increaseIndentation();
            emitter().emit(inputVarsList.get(i).getName() + "_in := cmd.bits.rs1(63,32)");
            emitter().emit(inputVarsList.get(i + 1).getName() + "_in := cmd.bits.rs1(31, 0)");
            emitter().emit(inputVarsList.get(i + 2).getName() + "_in := cmd.bits.rs1(63,32)");
            emitter().emit(inputVarsList.get(i + 3).getName() + "_in := cmd.bits.rs1(31, 0)");
            emitter().decreaseIndentation();
            emitter().emit("}");
        }

        emitter().emit("");
        // Check the firing condition and connect the input ports
        emitter().emit("when(fire){");
        emitter().increaseIndentation();
        for(int i = 0; i < (numInputs - 1) / 4; i++) {
            emitter().emit("acc.io." + inputVarsList.get(i).getName() + "_in := " + inputVarsList.get(i).getName() + "_in");
            emitter().emit("acc.io." + inputVarsList.get(i + 1).getName() + "_in := " + inputVarsList.get(i + 1).getName() + "_in");
            emitter().emit("acc.io." + inputVarsList.get(i + 2).getName() + "_in := " + inputVarsList.get(i + 2).getName() + "_in");
            emitter().emit("acc.io." + inputVarsList.get(i + 3).getName() + "_in := " + inputVarsList.get(i + 3).getName() + "_in");
        }

        int tmp = (numInputs % 4 == 0) ? 4 : numInputs % 4;
        for(int i = 0; i < tmp; i++) {
            String sourceReg = "rs1";
            String bits = "(63,32)";
            if(i % 2 == 1)
                bits = "(31, 0)";
            if(i > 1)
                sourceReg = "rs2";

            emitter().emit("acc.io." + inputVarsList.get(numInputs - tmp + i).getName() + "_in := cmd.bits."+ sourceReg + bits);
        }

        emitter().emit("acc.io.en := true.B");
        emitter().emit("returnReg := cmd.bits.inst.rd");
        emitter().decreaseIndentation();
        emitter().emit("}.otherwise {");
        emitter().increaseIndentation();
        emitter().emit("acc.io.en := false.B");
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");

        emitter().emit("io.resp.bits.rd   := returnReg");

        // So far we support only 4 output values - each 32 bits
        // TODO make this more dynamic and support more outputs
        if(outputVars.size() > 2) {
            emitter().emit("when(read){");
            emitter().increaseIndentation();
            if(outputVars.size() > 3)
                emitter().emit("io.resp.bits.data := Cat(acc.io." + outputVarsList.get(2).getName() + "_out, acc.io." + outputVarsList.get(3).getName() + "_out)");
            else
                emitter().emit("io.resp.bits.data := Cat(acc.io." + outputVarsList.get(2).getName() + "_out, 0.U)");

            emitter().emit("io.resp.valid     := true.B");
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emit(".otherwise{");
            emitter().increaseIndentation();
            emitter().emit("io.resp.bits.data := Cat(acc.io." + outputVarsList.get(0).getName() + "_out, acc.io." + outputVarsList.get(1).getName() + "_out)");
            emitter().emit("io.resp.valid     := acc.io.valid");
            emitter().decreaseIndentation();
            emitter().emit("}");
        }
        else{
            if (outputVars.size() > 1)
                emitter().emit("io.resp.bits.data := Cat(acc.io." + outputVarsList.get(0).getName() + "_out, acc.io." + outputVarsList.get(1).getName() + "_out)");
            else
                emitter().emit("io.resp.bits.data := Cat(acc.io." + outputVarsList.get(0).getName() + "_out, 0.U");

            emitter().emit("io.resp.valid     := acc.io.valid");
        }

        emitter().emit("cmd.ready         := true.B");
        emitter().emit("io.interrupt      := false.B");
        emitter().emit("io.busy           := false.B");
        emitter().emit("io.mem.req.valid  := false.B");

        emitter().decreaseIndentation();
        emitter().emit("}");

    }

    default Trackable trackable() { return backend().trackable(); }

    default void acceleratedTransition(String instanceName, ActorMachine actorMachine, Transition transition) {
        String fileName = instanceName + "Transition" + actorMachine.getTransitions().indexOf(transition) + ".scala";
        String fileName2 = instanceName + "_" + actorMachine.getTransitions().indexOf(transition) + "_accInterface.scala";
        //String fileName3 = instanceName + "_" + actorMachine.getTransitions().indexOf(transition) + "_subSystemConfig.scala";

        emitter().open(target().resolve(fileName));
        // Print the package name
        printPackage(instanceName, actorMachine.getTransitions().indexOf(transition));
        // Print the imports
        printImports();
        // Scala class definition
        generateClassDefinition(instanceName, actorMachine.getTransitions().indexOf(transition));
        emitter().emit("{");

        // IO declarations
        Set<VarDecl> inputVars = backend().ioVariables().IOVariablesRead(transition);
        Set<VarDecl> outputVars = backend().ioVariables().IOVariablesWrite(transition);
        generateIO(inputVars, outputVars);
        setInput(inputVars);

        // Declare initial version of all variables TODO > done
        findVarVersions(transition);
        emitter().increaseIndentation();
        declareVersions(inputVars);
        emitter().decreaseIndentation();

        // When you uncomment this part of code its going to visit all statement on the transition
        transition.getBody().forEach(this::execute);

        setOutput(outputVars);

        emitter().emit("}");

        emitter().close();
        emitter().open(target().resolve(fileName2));
        // Print the interface extension (different file)
        printAccInterfaceExtension(instanceName, actorMachine.getTransitions().indexOf(transition), inputVars, outputVars);

        emitter().close();

        ssaChisel().resetSSA();
    }

    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        emitter().emit("%s := %s", lvalue, rvalue);
    }

    default void copy(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        if (lvalueType.equals(rvalueType) && !isAlgebraicTypeList(lvalueType)) {
            emitter().emit("%s = %s", lvalue, rvalue);
        } else {
            String index = variables().generateTemp();
            emitter().emit("for (size_t %1$s = 0; %1$s < %2$s; %1$s++) {", index, lvalueType.getSize().getAsInt());
            emitter().increaseIndentation();
            copy(lvalueType.getElementType(), String.format("%s.data[%s]", lvalue, index), rvalueType.getElementType(), String.format("%s.data[%s]", rvalue, index));
            emitter().decreaseIndentation();
            emitter().emit("}");
        }
    }

    default void copy(SetType lvalueType, String lvalue, SetType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s)", type(lvalueType), lvalue, rvalue);
    }

    default void copy(MapType lvalueType, String lvalue, MapType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s)", type(lvalueType), lvalue, rvalue);
    }

    default void copy(StringType lvalueType, String lvalue, StringType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s)", type(lvalueType), lvalue, rvalue);
    }

    default void copy(AlgebraicType lvalueType, String lvalue, AlgebraicType rvalueType, String rvalue) {
        emitter().emit("copy_%s(&(%s), %s)", backend().algebraic().utils().name(lvalueType), lvalue, rvalue);
    }

    default void copy(AliasType lvalueType, String lvalue, AliasType rvalueType, String rvalue) {
        copy(lvalueType.getType(), lvalue, rvalueType.getType(), rvalue);
    }

    default void copy(TupleType lvalueType, String lvalue, TupleType rvalueType, String rvalue) {
        copy(backend().tuples().convert().apply(lvalueType), lvalue, backend().tuples().convert().apply(rvalueType), rvalue);
    }

    default boolean isAlgebraicTypeList(Type type) {
        if (!(type instanceof ListType)) {
            return false;
        }
        ListType listType = (ListType) type;
        if (listType.getElementType() instanceof AlgebraicType || backend().alias().isAlgebraicType(listType.getElementType())) {
            return true;
        } else {
            return isAlgebraicTypeList(listType.getElementType());
        }
    }

    default String declaration(Type type, String name) {
        //TODO: not all types are supported yet
        return "val " + name + " = Wire(" + type(type) + ")";
       // return type(type) + " " + name;
    }

    default String declaration(UnitType type, String name) { return "char " + name; }

    default String declaration(RefType type, String name) {
        return declaration(type.getType(), String.format("(*%s)", name));
    }

    default String declaration(LambdaType type, String name) {
        return type(type) + " " + name;
    }

    default String declaration(ProcType type, String name) {
        return type(type) + " " + name;
    }

    default String declaration(BoolType type, String name) { return "_Bool " + name; }

    default String declaration(StringType type, String name) { return type(type) + " " + name; }

    default String declaration(SetType type, String name) { return type(type) + " " + name; }

    default String declaration(MapType type, String name) { return type(type) + " " + name; }

    default String declaration(AlgebraicType type, String name) {
        return type(type) + " " + name;
    }

    default String declaration(AliasType type, String name) {
        return type(type) + (backend().alias().isAlgebraicType(type) ? " *" : " ") + name;
    }

    default String declaration(TupleType type, String name) {
        return declaration(backend().tuples().convert().apply(type), name);
    }

    String type(Type type);

    default String type(IntType type) {
        if (type.getSize().isPresent()) {
            int originalSize = type.getSize().getAsInt();
            /*int targetSize = 8;
            while (originalSize > targetSize) {
                targetSize = targetSize * 2;
            }*/
            //TODO currently sign is not taken into account
            return String.format(type.isSigned() ? "UInt(width = %d.W)" : "UInt(width = %d.W", originalSize/*targetSize*/);
        } else {
            return type.isSigned() ? "UInt(width = 32.W)" : "UInt(width = 32.W)";
        }
    }

    default String type(RealType type) {
        switch (type.getSize()) {
            case 32: return "UInt(width = 32.W)";
            case 64: return "UInt(width = 64.W)";
            default: throw new UnsupportedOperationException("Unknown real type.");
        }
    }

    default String type(UnitType type) {
        return "void";
    }

    default String type(ListType type) {
        return backend().callables().mangle(type).encode();
    }

    default String type(SetType type) {
        return backend().callables().mangle(type).encode();
    }

    default String type(MapType type) {
        return backend().callables().mangle(type).encode();
    }

    default String type(StringType type) {
        return "string_t";
    }

    default String type(BoolType type) { return "_Bool"; }

    default String type(CharType type) { return "char"; }

    default String type(RefType type) { return type(type.getType()) + "*"; }

    default String type(AlgebraicType type) {
        return backend().algebraic().utils().name(type);
    }

    default String type(AliasType type) {
        return type.getName();
    }

    default String type(TupleType type) {
        return type(backend().tuples().convert().apply(type));
    }

    default String type(LambdaType type) {
        return backend().callables().mangle(type).encode();
    }

    default String type(ProcType type) {
        return backend().callables().mangle(type).encode();
    }
    // ////////////////////////////////////////////////////////////////////////
    // -- Expressions
    // ////////////////////////////////////////////////////////////////////////

    String evaluate(Expression expr);

    default String evaluate(ExprVariable variable) {
        VarDecl decl = backend().varDecls().declaration(variable);
        String name = variables().declarationName(decl);
        String version = "_v" + Integer.toString(ssaChisel().getVersion(name));
        pipelineChisel().setLocalDelay(pipelineChisel().getGlobalDelay(name + version));    // Set the local delay
        return name + version;
    }

    default String evaluate(ExprRef ref) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprDeref deref) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprGlobalVariable variable) {
        VarDecl decl = backend().varDecls().declaration(variable);
        String name = variables().declarationName(decl);
        String version = "_v" + Integer.toString(ssaChisel().getVersion(name));
        pipelineChisel().setLocalDelay(pipelineChisel().getGlobalDelay(name + version));     // Set the local delay
        return name + version;
    }

    default String evaluate(ExprLiteral literal) {
        pipelineChisel().setLocalDelay(0);   // Set the local delay to zero
        switch (literal.getKind()) {
            case Integer:
                return literal.getText() + ".U";
            case True:
                return "true.B";
            case False:
                return "false.B";
            case Real:
                String text = literal.getText();
                float value = Float.parseFloat(text);
                String intBits =  Integer.toString(Float.floatToIntBits(value));
                return intBits + ".U";
            case String: {
                String tmp = variables().generateTemp();
                trackable().track(tmp, StringType.INSTANCE);
                emitter().emit("%s = init_%s(%s);", declaration(StringType.INSTANCE, tmp), type(StringType.INSTANCE), literal.getText());
                return tmp;
            }
            case Char:
                return literal.getText() + ".U";
            default:
                throw new UnsupportedOperationException(literal.getText());
        }
    }

    default String evaluate(ExprInput input) {
        pipelineChisel().setLocalDelay(0);   // Set the local delay to zero
        /**
         * Assign the input to the locally created variable.
         */
        IRNode parent = backend().tree().parent(input);
        if(parent instanceof VarDecl){
            emitter().emit("//Read input");
            VarDecl decl = (VarDecl) parent;
            return "io." + decl.getName() + "_in";
        }

        // TODO: The following is not tested. It may not be needed.
        String tmp = variables().generateTemp();
        emitter().emit("//Read input = %s", tmp);
        Type type = types().type(input);
        emitter().emit("%s", declaration(type, tmp));
        trackable().track(tmp, type);

        return tmp;
    }


    default String evaluate(ExprBinaryOp binaryOp) {
        assert binaryOp.getOperations().size() == 1 && binaryOp.getOperands().size() == 2;
        Type lhs = types().type(binaryOp.getOperands().get(0));
        Type rhs = types().type(binaryOp.getOperands().get(1));
        String operation = binaryOp.getOperations().get(0);
        switch (operation) {
            case "+":
                return evaluateBinaryAdd(lhs, rhs, binaryOp);
            case "-":
                return evaluateBinarySub(lhs, rhs, binaryOp);
           case "*":
                return evaluateBinaryTimes(lhs, rhs, binaryOp);
            case "/":
                return evaluateBinaryDiv(lhs, rhs, binaryOp);
/*            case "div":
                return evaluateBinaryIntDiv(lhs, rhs, binaryOp);
            case "%":
            case "mod":
                return evaluateBinaryMod(lhs, rhs, binaryOp);
            case "^":
                return evaluateBinaryExp(lhs, rhs, binaryOp);
            case "&":
                return evaluateBinaryBitAnd(lhs, rhs, binaryOp);

 */
            case "<<":
                return evaluateBinaryShiftL(lhs, rhs, binaryOp);
            case ">>":
                return evaluateBinaryShiftR(lhs, rhs, binaryOp);
    /*
            case "&&":
            case "and":
                return evaluateBinaryAnd(lhs, rhs, binaryOp);
            case "|":
                return evaluateBinaryBitOr(lhs, rhs, binaryOp);
            case "||":
            case "or":
                return evaluateBinaryOr(lhs, rhs, binaryOp);

            case "!=":
                return evaluateBinaryNeq(lhs, rhs, binaryOp);

     */
            case "=":
            case "==":
                return evaluateBinaryEq(lhs, rhs, binaryOp);
            case "<":
                return evaluateBinaryLtn(lhs, rhs, binaryOp);
            case "<=":
                return evaluateBinaryLeq(lhs, rhs, binaryOp);
            case ">":
                return evaluateBinaryGtn(lhs, rhs, binaryOp);
            case ">=":
                return evaluateBinaryGeq(lhs, rhs, binaryOp);
            case "in":
                return evaluateBinaryIn(lhs, rhs, binaryOp);
            default:
                throw new UnsupportedOperationException(operation);
        }
        //throw new Error("not implemented");
    }
    default String evaluateBinaryAdd(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryAdd(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        //TODO: If one input is float but the other is int, then convert the int to float

        if(lhs instanceof IntType && rhs instanceof IntType) // If both inputs are integers, then just use a "+"
            return String.format("(%s + %s)", evaluate(left), evaluate(right));

        String operator = generateFloatAdder();
        printDelayRegsOperator(operator, left, right);
        pipelineChisel().setLocalDelay(pipelineChisel().getLocalDelay() + pipelineChisel().getFloatAddDelay());

        return operator + ".io.out";
    }

    default String evaluateBinarySub(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinarySub(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        //TODO: If one input is float but the other is int, then convert the int to float

        if(lhs instanceof IntType && rhs instanceof IntType) // If both inputs are integers, then just use a "-"
            return String.format("(%s - %s)", evaluate(left), evaluate(right));

        String operator = generateFloatSubtractor();
        printDelayRegsOperator(operator, left, right);
        pipelineChisel().setLocalDelay(pipelineChisel().getLocalDelay() + pipelineChisel().getFloatSubDelay());

        return operator + ".io.out";
    }

    default String evaluateBinaryTimes(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryTimes(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        //TODO: If one input is float but the other one is int, then convert the int to float

        if(lhs instanceof IntType && rhs instanceof IntType) // If both inputs are integers, then just use a "*"
            return String.format("(%s * %s)", evaluate(left), evaluate(right));

        String operator = generateFloatMultiplier();
        printDelayRegsOperator(operator, left, right);
        pipelineChisel().setLocalDelay(pipelineChisel().getLocalDelay() + pipelineChisel().getFloatMulDelay());

        return operator + ".io.out";
    }

    default String evaluateBinaryDiv(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryDiv(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        //TODO: If one input is float but the other one is int, then convert the int to float

        if(lhs instanceof IntType && rhs instanceof IntType) // If both inputs are integers, then just use a "/"
            return String.format("(%s / %s)", evaluate(left), evaluate(right));

        String operator = generateFloatDivider();
        printDelayRegsOperator(operator, left, right);
        pipelineChisel().setLocalDelay(pipelineChisel().getLocalDelay() + pipelineChisel().getFloatDivDelay());

        return operator + ".io.out";
    }


    default String evaluateBinaryIntDiv(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryIntDiv(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s / %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryMod(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryMod(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s %% %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryExp(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryExp(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s << %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryMod(RealType lhs, IntType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("pow(%s, %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryBitAnd(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryBitAnd(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s & %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryShiftL(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryShiftL(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s << %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryShiftR(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryShiftR(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s >> %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryAnd(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryAnd(BoolType lhs, BoolType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        String andResult = variables().generateTemp();
        emitter().emit("_Bool %s;", andResult);
        emitter().emit("if (%s) {", evaluate(left));
        emitter().increaseIndentation();
        trackable().enter();
        emitter().emit("%s = %s;", andResult, evaluate(right));
        trackable().exit();
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        trackable().enter();
        emitter().emit("%s = false;", andResult);
        trackable().exit();
        emitter().decreaseIndentation();
        emitter().emit("}");
        return andResult;
    }

    default String evaluateBinaryBitOr(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryBitOr(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s | %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryOr(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryOr(BoolType lhs, BoolType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        String orResult = variables().generateTemp();
        emitter().emit("_Bool %s;", orResult);
        emitter().emit("if (%s) {", evaluate(left));
        emitter().increaseIndentation();
        emitter().emit("%s = true;", orResult);
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        trackable().enter();
        emitter().emit("%s = %s;", orResult, evaluate(right));
        trackable().exit();
        emitter().decreaseIndentation();
        emitter().emit("}");
        return orResult;
    }

    default String evaluateBinaryEq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return compare(types().type(left), evaluate(left), types().type(right), evaluate(right));
    }

    default String evaluateBinaryNeq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return "!" + compare(types().type(left), evaluate(left), types().type(right), evaluate(right));
    }

    default String evaluateBinaryLtn(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryLtn(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s < %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryLtn(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLtn(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLeq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryLeq(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s <= %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryLeq(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_equal_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLeq(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_equal_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGtn(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryGtn(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s > %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryGtn(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGtn(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGeq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryGeq(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s >= %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryGeq(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_equal_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGeq(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_equal_%2$s(%3$s, %4$s);", tmp, type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, Type rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryIn(Type lhs, ListType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        String index = variables().generateTemp();
        String elem = evaluate(binaryOp.getOperands().get(0));
        String list = evaluate(binaryOp.getOperands().get(1));
        emitter().emit("%s = false;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && !(%3$s); %1$s++) {", index, rhs.getSize().getAsInt(), tmp);
        emitter().increaseIndentation();
        emitter().emit("%s |= %s;", tmp, compare(lhs, elem, rhs.getElementType(), String.format("%s.data[%s]", list, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, SetType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, MapType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, StringType rhs, ExprBinaryOp binaryOp) {// NOT IMPLEMENTED
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluate(ExprUnaryOp unaryOp) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprComprehension comprehension) {
        throw new Error("not implemented");
    }

    String evaluateComprehension(ExprComprehension comprehension, Type t);

    default String evaluateComprehension(ExprComprehension comprehension, ListType t) {
        throw new Error("not implemented");
    }

    void evaluateListComprehension(Expression comprehension, String result, String index);

    default void evaluateListComprehension(ExprComprehension comprehension, String result, String index) {
        throw new Error("not implemented");
    }

    default void evaluateListComprehension(ExprList list, String result, String index) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprList list) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprIndexer indexer) {
        return exprIndexing(types().type(indexer.getStructure()), indexer);
    }

    String exprIndexing(Type type, ExprIndexer indexer);
    default String exprIndexing(ListType type, ExprIndexer indexer) {
        return String.format("%s.data[%s]", evaluate(indexer.getStructure()), evaluate(indexer.getIndex()));
    }

    default String exprIndexing(MapType type, ExprIndexer indexer) {
        throw new Error("not implemented");
    }

    default String exprIndexing(StringType type, ExprIndexer indexer) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprIf expr) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprApplication apply) {

        String functName = "";
        Expression expr = apply.getFunction();
       if(expr instanceof ExprVariable) {
           functName = ((ExprVariable) expr).getVariable().getOriginalName();

            if(functName.contains("sqrt")){
                // Instantiate a sqrt block -- the result should be put in sqrtx
                Expression parameter = apply.getArgs().get(0);
                String sqrt = generateFloatSquareRoot();
                emitter().emit(sqrt + ".io.en := true.B");
                setUnaryOpDelay(sqrt, parameter);
                pipelineChisel().setLocalDelay(pipelineChisel().getLocalDelay() + pipelineChisel().getFloatSqrtDelay());
                return sqrt + ".io.out";
            }
        }
        return "Function call not supported";
    }

    default String evaluate(ExprLambda lambda) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprProc proc) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprLet let) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprTypeConstruction construction) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprTypeAssertion assertion) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprField field) {
        return String.format("%s->%s", evaluate(field.getStructure()), field.getField().getName());
    }

    default String evaluate(ExprCase caseExpr) {
        throw new Error("not implemented");
    }

    // ////////////////////////////////////////////////////////////////////////
    // -- Statements
    // ////////////////////////////////////////////////////////////////////////

    void execute(Statement stmt);

    default void execute(StmtConsume consume) {

        //TODO: Nothing to do here
        //This is called after the write statement
    }

    default void execute(StmtWrite write) {
        //TODO fix the output assignment
        emitter().emit("//Write the outputs");
    }

    default void execute(StmtAssignment assign) {
        trackable().enter();
        Type ltype = types().type(assign.getLValue());
        String lvalue = lvalue(assign.getLValue());
        // SSA part
        String version = "_v" + Integer.toString(ssaChisel().getVersion(lvalue) + 1);
        //String d = declaration(ltype, lvalue + version);
        //trackable().track(lvalue + version, ltype);
        //emitter().emit("%s", d);
        // End of SSA
        Type rtype = types().type(assign.getExpression());
        String rvalue = evaluate(assign.getExpression());
        // SSA part
        ssaChisel().increaseVersion(lvalue);
        // End of SSA
        copy(ltype, lvalue + version, rtype, rvalue);
        pipelineChisel().setGlobalDelay(lvalue + version, pipelineChisel().getLocalDelay());
        emitter().emit("// " + lvalue + version + " delay: " + pipelineChisel().getLocalDelay() + " cycles");
        emitter().emit("");
        trackable().exit();
    }

    default void execute(StmtBlock block) {
        emitter().emit("{");
        emitter().increaseIndentation();
        trackable().enter();
        backend().callables().declareEnvironmentForCallablesInScope(block);
        for (VarDecl decl : block.getVarDecls()) {
            Type ltype = types().declaredType(decl);
            String declarationName = variables().declarationName(decl);

            // Add the variable to the version list for SSA
            ssaChisel().setVersion(declarationName, 0);
            String version = "_v0";

            String d = declaration(ltype, declarationName + version);
            trackable().track(declarationName, ltype);
            // The version 0s are not used, therefore we won't generate the declarations //TODO check this later
            //emitter().emit("%s", d);
            if (decl.getValue() != null) {
                copy(ltype, declarationName + version, types().type(decl.getValue()), evaluate(decl.getValue()));
            }
        }
        block.getStatements().forEach(this::execute);
        trackable().exit();
        emitter().decreaseIndentation();
        emitter().emit("}");

        //throw new Error("not implemented");
    }

    default void execute(StmtIf stmt) {
        trackable().enter();
        String condition = evaluate(stmt.getCondition());
        emitter().emit("when (%s) {", condition);
        emitter().increaseIndentation();
        // Store the variable versions before the if body (this will be used after the body for the phi functions)
        HashMap<String, Integer> oldVarVersions = new HashMap<String, Integer>();
        //HashMap<String, Integer> oldVarVersionsElse = new HashMap<String, Integer>();
        HashMap<String, Integer> varVersions = ssaChisel().getPublicVarVersions();
        oldVarVersions.putAll(varVersions);

        trackable().enter();
        stmt.getThenBranch().forEach(this::execute);
        trackable().exit();
        emitter().decreaseIndentation();
        if (stmt.getElseBranch().size() != 0) {
            oldVarVersions.putAll(varVersions); // store the var versions
            emitter().emit("} .otherwise {");
            emitter().increaseIndentation();
            trackable().enter();
            stmt.getElseBranch().forEach(this::execute);
            trackable().exit();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

        // Print the phi functions
        if (stmt.getElseBranch().size() == 0){
            printPhiFunctions(condition, varVersions, oldVarVersions);
        }
        else{
            printPhiFunctions(condition, oldVarVersions, varVersions);
        }
        trackable().exit();
    }

    default void execute(StmtForeach foreach) {
        throw new Error("not implemented");
    }

    default void execute(StmtCall call) {
        trackable().enter();
/*
        String proc = "";
        if(call.getProcedure() instanceof ExprVariable) {
            proc = ((ExprVariable) call.getProcedure()).getVariable().getOriginalName();

            if(proc.contains("sqrt")){
                // Instantiate a sqrt block -- the result should be put in sqrtx
                Expression parameter = call.getArgs().get(0);
                String param = evaluate(parameter);

                String varName = "";
                HashMap versions = ssaChisel().getPublicVarVersions();
                for(Object key : versions.keySet()){
                    if (((String)key).contains("sqrtx")) {
                        varName = (String) key;
                        break;
                    }
                }

                ssaChisel().increaseVersion(varName);
                int version = ssaChisel().getVersion(varName);

                String sqrt = generateFloatSquareRoot();
                emitter().emit(sqrt + ".io.en := true.B");
                emitter().emit(sqrt + ".io.in := " + param);
                emitter().emit(varName + "_v" + version + " := sqrt.io.out");
                //TODO set the delay
            }
        }
*/
        trackable().exit();
    }

    default void execute(StmtWhile stmt) {
        throw new Error("not implemented");
    }

    default void execute(StmtCase caseStmt) {
        throw new Error("not implemented");
    }
    String lvalue(LValue lvalue);

    default String lvalue(LValueVariable var) {
        VarDecl decl = backend().varDecls().declaration(var);
        return variables().declarationName(decl);
        //return variables().name(var.getVariable());
    }

    default String lvalue(LValueDeref deref) {
        return "(*"+lvalue(deref.getVariable())+")";
    }

    default String lvalue(LValueIndexer indexer) {
        return lvalueIndexing(types().type(indexer.getStructure()), indexer);
    }

    default String lvalue(LValueField field) {
        return String.format("%s->%s", lvalue(field.getStructure()), field.getField().getName());
    }

    default String lvalue(LValueNth nth) {
        return String.format("%s->%s", lvalue(nth.getStructure()), "_" + nth.getNth().getNumber());
    }

    String lvalueIndexing(Type type, LValueIndexer indexer);
    default String lvalueIndexing(ListType type, LValueIndexer indexer) {
        return String.format("%s.data[%s]", lvalue(indexer.getStructure()), evaluate(indexer.getIndex()));
    }

    default String lvalueIndexing(MapType type, LValueIndexer indexer) {
        String index = variables().generateTemp();
        String map = lvalue(indexer.getStructure());
        String key = evaluate(indexer.getIndex());
        emitter().emit("size_t %s;", index);
        emitter().emit("for (%1$s = 0; %1$s < %2$s->size; %1$s++) {", index, map);
        emitter().increaseIndentation();
        emitter().emit("if (%s) break;", compare(type.getKeyType(), key, type.getValueType(), String.format("%s.data[%s]->key", map, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return String.format("%s->data[%s].value", map, index);
    }

    default String lvalueIndexing(StringType type, LValueIndexer indexer) {
        return String.format("%s[%s]", lvalue(indexer.getStructure()), evaluate(indexer.getIndex()));
    }

    default String compare(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        return String.format("(%s === %s)", lvalue, rvalue);
    }

    default String compare(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        String index = variables().generateTemp();
        emitter().emit("%s = true;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && %3$s; %1$s++) {", index, lvalueType.getSize().getAsInt(), tmp);
        emitter().increaseIndentation();
        emitter().emit("%s &= %s;", tmp, compare(lvalueType.getElementType(), String.format("%s.data[%s]", lvalue, index), rvalueType.getElementType(), String.format("%s.data[%s]", rvalue, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return tmp;
    }

    default String compare(SetType lvalueType, String lvalue, SetType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(MapType lvalueType, String lvalue, MapType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(StringType lvalueType, String lvalue, StringType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(AlgebraicType lvalueType, String lvalue, AlgebraicType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = compare_%s(%s, %s);", declaration(BoolType.INSTANCE, tmp), backend().algebraic().utils().name(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(AliasType lvalueType, String lvalue, AliasType rvalueType, String rvalue) {
        return compare(lvalueType.getType(), lvalue, rvalueType.getType(), rvalue);
    }

    default String compare(TupleType lvalueType, String lvalue, TupleType rvalueType, String rvalue) {
        return compare(backend().tuples().convert().apply(lvalueType), lvalue, backend().tuples().convert().apply(rvalueType), rvalue);
    }
}
