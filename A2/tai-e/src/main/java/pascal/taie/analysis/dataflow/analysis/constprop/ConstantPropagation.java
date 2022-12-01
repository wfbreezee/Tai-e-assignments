/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;

import java.util.List;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
        CPFact cpfact = new CPFact();
        IR ir= cfg.getIR();
        List<Var> params = ir.getParams();
        if(params!=null){
            for(Var var:params){
                cpfact.update(var,Value.getNAC());
            }
        }

        return cpfact;
        //return null;
    }

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
        return new CPFact();
        //return null;
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        for (Var v1:fact.keySet())
        {
                Value value = meetValue(fact.get(v1),target.get(v1));
                target.update(v1,value);

        }
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
        if(v1.isNAC()||v2.isNAC())  return Value.getNAC();

        if(v1.isUndef())  return v2;
        if(v2.isUndef())  return v1;

        if(v1.isConstant()&&v2.isConstant())
        {
            if(v1.getConstant()==v2.getConstant())  return v1;
            else return Value.getNAC();
        }

        return Value.getUndef();
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me

        if(!stmt.getDef().isPresent()){
            out.copyFrom(in);
            return false;
        }
        else{
           Var leftv= (Var)stmt.getDef().get();
           //来判断一个变量能否储存 int 类型的值，不能的话直接忽略掉
           if(!canHoldInt(leftv)) return false;

           CPFact old_out = out.copy();
           out.copyFrom(in);
           out.remove(leftv);
            List<RValue> rValueList = stmt.getUses();
            if(rValueList.size()==1){
                Value val = evaluate(rValueList.get(0), in);
                out.update(leftv, val);
            } else if (rValueList.size()>1) {
                for (Exp exp:rValueList) {
                    if(exp instanceof BinaryExp) {
                        Value val = evaluate(exp, in);
                        out.update(leftv, val);
                    }
                    else{
                        //等号右侧为其它表达式的赋值语句，例如方法调用（x = m(...)）和字段 load（x = o.f），
                        // 你需要对它们进行保守的近似处理（也许会不够精确），即把它们当作 x = NAC。
                       if(!(exp instanceof Var))
                           out.update(leftv,Value.getNAC());
                    }
                }
            }


            return !old_out.equals(out);
        }
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // TODO - finish me
        if(exp instanceof Var){
            return in.get((Var)exp); //
        } else if(exp instanceof IntLiteral){
            return Value.makeConstant(((IntLiteral) exp).getValue());
        }
        else if (exp instanceof BinaryExp) {
            Var r1 = ((BinaryExp) exp).getOperand1();
            Var r2 = ((BinaryExp) exp).getOperand2();
            Value r1v = in.get(r1);
            Value r2v = in.get(r2);
            if (r1v.isConstant() && r2v.isConstant()) {
                int v1 = r1v.getConstant();
                int v2 = r2v.getConstant();
                int v = 0;
                if (exp instanceof ArithmeticExp) {
                    ArithmeticExp.Op op = ((ArithmeticExp) exp).getOperator();
                    switch (op) {
                        case ADD -> v = v1 + v2;
                        case DIV -> v = v1 / v2;
                        case MUL -> v = v1 * v2;
                        case SUB -> v = v1 - v2;
                        case REM -> v = v1 % v2;
                    }
                } else if (exp instanceof BitwiseExp) {
                    BitwiseExp.Op op = ((BitwiseExp) exp).getOperator();
                    switch (op) {
                        case OR -> v = v1 | v2;
                        case AND -> v = v1 & v2;
                        case XOR -> v = v1 ^ v2;
                    }
                } else if (exp instanceof ConditionExp) {
                    ConditionExp.Op op = ((ConditionExp) exp).getOperator();
                    switch (op){
                        case EQ -> v = v1==v2?1:0;
                        case GE -> v = v1>=v2?1:0;
                        case GT -> v = v1>v2?1:0;
                        case LE -> v = v1<=v2?1:0;
                        case LT -> v = v1<v2?1:0;
                        case NE -> v = v1!=v2?1:0;
                    }
                } else if (exp instanceof ShiftExp) {
                    ShiftExp.Op op = ((ShiftExp) exp).getOperator();
                    switch (op) {
                        case SHL -> v = v1 << v2;
                        case SHR -> v = v1 >> v2;
                        case USHR -> v = v1 >>> v2;
                    }
                }

                return Value.makeConstant(v);

            } else if (r1v.isNAC() || r2v.isNAC()) {
                return Value.getNAC();
            } else {
                return Value.getUndef();
            }

        }

        return null;
    }
}
