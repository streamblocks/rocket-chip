package se.lth.cs.tycho.backend.chisel;

import java.util.HashMap;

public class PipelineChisel {

    private int floatMulDelay  = 1;
    private int floatAddDelay  = 3;
    private int floatDivDelay  = 5;
    private int floatSubDelay  = 3;
    private int floatSqrtDelay = 5;

    private int localDelay;
    private HashMap<String, Integer> globalDelays;

    public PipelineChisel() {
        globalDelays = new HashMap<String, Integer>();
    }

    public int getLocalDelay() {
        return localDelay;
    }

    public void setLocalDelay(int localDelay) {
        this.localDelay = localDelay;
    }

    public HashMap<String, Integer> getGlobalDelays() {
        return globalDelays;
    }

    public void setGlobalDelay(String varName, int delay) {
        if(globalDelays.containsKey(varName))
            globalDelays.replace(varName, delay);
        else
            globalDelays.put(varName, delay);
    }

    public int getGlobalDelay(String varName){
        if(globalDelays.containsKey(varName))
            return globalDelays.get(varName);
        else
            return 0;
    }

    public int getLongestDelay(){
        int longestDelay = 0;
        for (int i: globalDelays.values()) {
            if (i > longestDelay)
                longestDelay = i;
        }
        return longestDelay;
    }

    public int getFloatMulDelay(){
        return floatMulDelay;
    }

    public int getFloatAddDelay() {
        return floatAddDelay;
    }

    public int getFloatDivDelay() {
        return floatDivDelay;
    }

    public int getFloatSqrtDelay() {
        return floatSqrtDelay;
    }

    public int getFloatSubDelay() {
        return floatSubDelay;
    }
}
