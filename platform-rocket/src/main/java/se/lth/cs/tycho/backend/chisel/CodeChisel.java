package se.lth.cs.tycho.backend.chisel;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.backend.c.Backend;
import se.lth.cs.tycho.backend.c.Emitter;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;

import static org.multij.BindingKind.LAZY;

@Module
public interface CodeChisel {
    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Path target() {
        return backend().context().getConfiguration().get(Compiler.targetPath);
    }

    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

    default void acceleratedTransition(String instanceName, ActorMachine actorMachine, Transition transition) {
        String fileName = instanceName + "_transition_" + actorMachine.getTransitions().indexOf(transition) + ".scala";
        emitter().open(target().resolve(fileName));

        // When you uncomment this part of code its going to visit all statement on the transition
        //transition.getBody().forEach(this::execute);

        emitter().close();
    }

    // ////////////////////////////////////////////////////////////////////////
    // -- Expressions
    // ////////////////////////////////////////////////////////////////////////

    String evaluate(Expression expr);

    default String evaluate(ExprVariable variable) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprRef ref) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprDeref deref) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprGlobalVariable variable) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprLiteral literal) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprInput input) {
        throw new Error("not implemented");
    }


    default String evaluate(ExprBinaryOp binaryOp) {
        throw new Error("not implemented");
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
        throw new Error("not implemented");
    }

    default String evaluate(ExprIf expr) {
        throw new Error("not implemented");
    }

    default String evaluate(ExprApplication apply) {
        throw new Error("not implemented");
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
        throw new Error("not implemented");
    }

    default String evaluate(ExprCase caseExpr) {
        throw new Error("not implemented");
    }

    // ////////////////////////////////////////////////////////////////////////
    // -- Statements
    // ////////////////////////////////////////////////////////////////////////

    void execute(Statement stmt);

    default void execute(StmtConsume consume) {
        throw new Error("not implemented");
    }

    default void execute(StmtWrite write) {
        throw new Error("not implemented");
    }

    default void execute(StmtAssignment assign) {
        throw new Error("not implemented");
    }

    default void execute(StmtBlock block) {
        throw new Error("not implemented");
    }

    default void execute(StmtIf stmt) {
        throw new Error("not implemented");
    }

    default void execute(StmtForeach foreach) {
        throw new Error("not implemented");
    }

    default void execute(StmtCall call) {
        throw new Error("not implemented");
    }

    default void execute(StmtWhile stmt) {
        throw new Error("not implemented");
    }

    default void execute(StmtCase caseStmt) {
        throw new Error("not implemented");
    }

}
