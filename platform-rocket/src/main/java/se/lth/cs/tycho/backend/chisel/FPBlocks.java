package se.lth.cs.tycho.backend.chisel;

public class FPBlocks {

    /*
        Arithmetic blocks generators
        Generate and print the block and return the block name
    *//*
    public String generateFloatAdder(){
        String adder = "fAdd" + backend().uniqueNumbers().next();
        emitter().emit("val " + adder + " = Module(new FPAdd(32))");
        return adder;
    }
    public String generateAdderWithSize(int size){
        //TODO: Implement
        return "generateAdderWithSize is not implemented";
    }

    public String generateFloatSubtractor(){
        String subtractor = "fSub" + backend().uniqueNumbers().next();
        emitter().emit("val " + subtractor + " = Module(new FPSub(32))");
        return subtractor;
    }

    public String generateFloatMultiplier(){
        String multiplier = "fMul" + backend().uniqueNumbers().next();
        emitter().emit("val " + multiplier + " = Module(new FPMult(32))");
        return multiplier;
    }
    public String generateMultiplierWithSize(int size){
        //TODO: Implement
        return "generateMultiplierWithSize is not implemented";
    }

    public String generateFloatDivider(){
        String divider = "fDiv" + backend().uniqueNumbers().next();
        emitter().emit("val " + divider + " = Module(new FPDiv(32)");
        return divider;
    }
    default String generateDividerWithSize(int size){
        //TODO: Implement
        return "generateDividerWithSize is not implemented";
    }
*/
    /* End of arithmetic operation block generation */
}
