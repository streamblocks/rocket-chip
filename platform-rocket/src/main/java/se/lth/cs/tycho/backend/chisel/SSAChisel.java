package se.lth.cs.tycho.backend.chisel;

import se.lth.cs.tycho.backend.c.Backend;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.ExprComprehension;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.type.Type;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class SSAChisel {

    private HashMap<String, Integer> publicVarVersions;
    private HashMap<String, Integer> localVarVersions;
    private HashMap<String, Type> localVarTypes;

    public SSAChisel () {
        publicVarVersions = new HashMap<String, Integer>();
        localVarVersions = new HashMap<String, Integer>();
        localVarTypes = new HashMap<String, Type>();
    }

    public void setVersion(String varName, int version){
        if(!publicVarVersions.containsKey(varName))
            publicVarVersions.put(varName, version);
        else
            publicVarVersions.replace(varName, version);
    }

    public int getVersion(String varName){
        if(publicVarVersions.containsKey(varName))
            return publicVarVersions.get(varName);
        else
            return 0;
    }
    public HashMap<String, Integer> getPublicVarVersions () {
        return publicVarVersions;
    }

    public HashMap<String, Integer> getLocalVarVersions () {
        return localVarVersions;
    }

    public void increaseVersion(String varName){
        if(publicVarVersions.containsKey(varName))
            publicVarVersions.replace(varName, publicVarVersions.get(varName) + 1);
        else
            publicVarVersions.put(varName, 0);
    }

    public void setLocalVersion(String varName, int version){
        if(!localVarVersions.containsKey(varName))
            localVarVersions.put(varName, version);
        else
            localVarVersions.replace(varName, version);
    }

    public int getLocalVersion(String varName){
        if(localVarVersions.containsKey(varName))
            return localVarVersions.get(varName);
        else
            return 0;
    }

    public Type getLocalType(String varName){
        if(localVarTypes.containsKey(varName))
            return localVarTypes.get(varName);
        else
            return null;
    }

    public void increaseLocalVersion(String varName){
        if(localVarVersions.containsKey(varName))
            localVarVersions.replace(varName, localVarVersions.get(varName) + 1);
        else
            localVarVersions.put(varName, 1);
    }

    public String phiFunction(String condition, HashMap<String, Integer> versions1, HashMap<String, Integer> versions2 ){
        // Generate a phi function for each variable
        String returnValue = "";
        for (String variable : versions1.keySet()){
            if (versions1.containsKey(variable)){
                if(versions1.get(variable) != versions2.get(variable)){
                    //Insert phi function as a mux in Chisel
                    returnValue += "val " + variable + "_v" + Integer.toString(publicVarVersions.get(variable) + 1) + " = Mux(" +
                            condition + ", " + variable + "_v" + Integer.toString(versions1.get(variable)) +
                            ", " + variable + "_v" + Integer.toString(versions2.get(variable)) + ")";
                    increaseVersion(variable);
                }
            }
        }

        return returnValue;
    }

    public Set<LValue> findLValues(IRNode node) {
        Set<LValue> lValues = new LinkedHashSet<>();

        if (node instanceof StmtAssignment){
            return Collections.singleton(((StmtAssignment) node).getLValue());
        }
        else
            node.forEachChild(child -> lValues.addAll(findLValues(child)));

        return lValues;
    }

    public void setLocalVarType (String varName, Type ltype){
        if(!localVarTypes.containsKey(varName))
            localVarTypes.put(varName, ltype);
    }

    public void resetSSA(){
        publicVarVersions.clear();
        localVarVersions.clear();
        localVarTypes.clear();
    }
}
