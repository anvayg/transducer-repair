package solver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sat4j.specs.TimeoutException;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.IntSort;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Sort;
import com.microsoft.z3.Status;
import com.microsoft.z3.Symbol;
import com.microsoft.z3.TupleSort;

import automata.SFAOperations;
import automata.sfa.SFA;
import automata.sfa.SFAMove;
import theory.BooleanAlgebraSubst;
import theory.characters.CharConstant;
import theory.characters.CharFunc;
import theory.characters.CharPred;
import transducers.sft.SFT;
import transducers.sft.SFTInputMove;
import transducers.sft.SFTMove;
import utilities.Pair;

public class Constraints {
	
	/* Fields/instance variables */
	Context ctx;
	SFA<CharPred, Character> source; 
	SFA<CharPred, Character> target; 
	Set<Character> alphabet;
	HashMap<Character, Integer> alphabetMap;
	BooleanAlgebraSubst<CharPred, CharFunc, Character> ba;
		
	/* Constructor */
	public Constraints(Context ctx, SFA<CharPred, Character> source, SFA<CharPred, Character> target, HashMap<Character, 
				Integer> alphabetMap, BooleanAlgebraSubst<CharPred, CharFunc, Character> ba) {
		this.ctx = ctx;
		this.source = source;
		this.target = target;
		this.alphabet = alphabetMap.keySet();
		this.alphabetMap = alphabetMap;
		this.ba = ba;
	}
	
	/*
	 * Reverse injective map
	 */
	public static <A, B> HashMap<B, A> reverseMap(HashMap<A, B> map) { 
		HashMap<B, A> reverseMap = new HashMap<B, A>();
		
		for (A key : map.keySet()) {
			reverseMap.put(map.get(key), key);
		}
		
		return reverseMap;
	}
	
	/* 
	 * Converts a string from an input-output example to an int array using the alphabet map
	 */
	public static int[] stringToIntArray(HashMap<Character, Integer> alphabetMap, String str) {
		int[] arr = new int[str.length()];
		
		for (int i = 0; i < str.length(); i++) {
			arr[i] = alphabetMap.get(str.charAt(i));
		}
		
		return arr;
	}
	
	/* Method for mkConstraints without examples and template */
	public SFT<CharPred, CharFunc, Character> mkConstraints(int numStates, int bound, 
			int[] fraction, boolean debug) throws TimeoutException {
		List<Pair<String, String>> empty = new ArrayList<Pair<String, String>>();
		return mkConstraints(ctx, ctx.mkSolver(), alphabetMap, source, target, numStates, bound, fraction, empty, null, ba, null, debug);
	}
	
	/* Method for mkConstraints without template */
	public SFT<CharPred, CharFunc, Character> mkConstraints(int numStates, int bound, int[] fraction, 
			List<Pair<String, String>> ioExamples, boolean debug) throws TimeoutException { 	// take out debug later
		return mkConstraints(ctx, ctx.mkSolver(), alphabetMap, source, target, numStates, bound, fraction, ioExamples, null, ba, null, debug);
	}
	
	/* Method for mkConstraints */
	public SFT<CharPred, CharFunc, Character> mkConstraints(int numStates, int bound, int[] fraction, 
			List<Pair<String, String>> ioExamples, SFA<CharPred, Character> template, SFT<CharPred, CharFunc, Character> solution, String smtFile, boolean debug) throws TimeoutException { 	// take out debug later
		return mkConstraints(ctx, ctx.mkSolver(), alphabetMap, source, target, numStates, bound, fraction, ioExamples, template, ba, smtFile, debug);
	}
		
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static SFT<CharPred, CharFunc, Character> mkConstraints(Context ctx, Solver solver, HashMap<Character, Integer> alphabetMap, 
			SFA<CharPred, Character> source, SFA<CharPred, Character> target, int numStates, int length, int[] fraction, 
			List<Pair<String, String>> ioExamples, SFA<CharPred, Character> template, BooleanAlgebraSubst<CharPred, CharFunc, Character> ba, 
			String smtFile, boolean debug) throws TimeoutException {
		
		/* int and bool sorts */
		Sort I = ctx.getIntSort();
		Sort B = ctx.getBoolSort();
		
		Expr<IntSort> numStatesInt = ctx.mkInt(numStates);
		Expr<IntSort> alphabetSize = ctx.mkInt(alphabetMap.size());
		Expr<IntSort> zero = ctx.mkInt(0);
		Expr<IntSort> bound = ctx.mkInt(length);
		
		/* declare d_1:  */
		Sort[] argsToD1 = new Sort[]{ I, I, I };
		FuncDecl<Sort> d1 = ctx.mkFuncDecl("d1", argsToD1, I);
		
		/* declare out_len */
		Sort[] argsToOutLen = new Sort[]{ I, I };
		FuncDecl<Sort> out_len = ctx.mkFuncDecl("out_len", argsToOutLen, I);
		
		/* declare d_2 : Q x \Sigma -> Q */
		Sort[] argsToD2 = new Sort[]{ I, I };
		FuncDecl<Sort> d2 = ctx.mkFuncDecl("d2", argsToD2, I);
		
		/* restrict range of d_1, d_2 and out_len */
		for (int i = 0; i < numStates; i++) {	// q 
			Expr<IntSort> q = ctx.mkInt(i);
			
			for (int move : alphabetMap.values())  {
				Expr a = ctx.mkInt(move); 
				
				/* 0 <= out_len(q, a) <= l */
				Expr outLenExpr = out_len.apply(q, a);
				solver.add(ctx.mkLe(zero, outLenExpr));
				solver.add(ctx.mkLe(outLenExpr, bound));
				
				/* make variable q' = d2(q, a) */
				Expr qPrime = d2.apply(q, a);
				
				/* 0 <= qPrime < numStates; range only needs to be encoded once */
				solver.add(ctx.mkLe(zero, qPrime));
				solver.add(ctx.mkLt(qPrime, numStatesInt));
				
				for (int l = 0; l < length; l++) {
					Expr<IntSort> index = ctx.mkInt(l);
					Expr d1exp = d1.apply(q, a, index);
					
					/* 0 <= d1(q, a, index) < alphabetSize */
					solver.add(ctx.mkLe(zero, d1exp));
					solver.add(ctx.mkLt(d1exp, alphabetSize)); 
				}
			}
		}
		
		/* declare x : Q_R x Q x Q_T -> {1, 0} */
		Sort[] argsToX = new Sort[]{ I, I, I };
		FuncDecl<Sort> x = ctx.mkFuncDecl("x", argsToX, B);
		
		/* initial states: x(q^0_R, q^0, q^0_T) */
		Expr<IntSort> sourceInit = ctx.mkInt(source.getInitialState());
		Expr<IntSort> targetInit = ctx.mkInt(target.getInitialState());
		Expr res = x.apply(sourceInit, zero, targetInit);
		solver.add(res);
		
		/* d_R: transition relation of source */
		Sort[] argsToDR = new Sort[]{ I, I };
		FuncDecl<Sort> dR = ctx.mkFuncDecl("dR", argsToDR, I);
		
		/* encode d_R */
		Collection<SFAMove<CharPred, Character>> sourceTransitions = source.getTransitions();
		for (SFAMove<CharPred, Character> transition : sourceTransitions) {
			Integer stateFrom = transition.from;
			Expr<IntSort> q1 = ctx.mkInt(stateFrom);
			
			Character move = transition.getWitness(ba); // there should only be 1
			Expr<IntSort> a = ctx.mkInt(alphabetMap.get(move));
			
			Integer stateTo = transition.to;
			Expr<IntSort> q2 = ctx.mkInt(stateTo);
			
			Expr dexp = dR.apply(q1, a);
			solver.add(ctx.mkEq(dexp, q2));
		}
		
		/* d_T: transition relation of target */
		Sort[] argsToDT = new Sort[]{ I, I };
		FuncDecl<Sort> dT = ctx.mkFuncDecl("dT", argsToDT, I);
		
		/* encode d_T */
		Collection<SFAMove<CharPred, Character>> targetTransitions = target.getTransitions();
		for (SFAMove<CharPred, Character> transition : targetTransitions) {
			Integer stateFrom = transition.from;
			Expr<IntSort> q1 = ctx.mkInt(stateFrom);
			
			Character move = transition.getWitness(ba); // there should only be 1
			Expr<IntSort> a = ctx.mkInt(alphabetMap.get(move));
			
			Integer stateTo = transition.to;
			Expr<IntSort> q2 = ctx.mkInt(stateTo);
			
			Expr dexp = dT.apply(q1, a);
			solver.add(ctx.mkEq(dexp, q2));
		}
		
		/* declare f_R : Q -> {0, 1} */
		FuncDecl<Sort> f_R = ctx.mkFuncDecl("f_R", I, B);
		for (Integer sourceState : source.getStates()) {
			Expr<IntSort> stateInt = ctx.mkInt(sourceState);
			Expr c = f_R.apply(stateInt);
			if (!source.isFinalState(sourceState)) c = ctx.mkNot(c);
			solver.add(c);
		}
		
		/* declare f_T : Q -> {0, 1} */
		FuncDecl<Sort> f_T = ctx.mkFuncDecl("f_T", I, B);
		for (Integer targetState : target.getStates()) {
			Expr<IntSort> stateInt = ctx.mkInt(targetState);
			Expr c = f_T.apply(stateInt);
			if (!target.isFinalState(targetState)) c = ctx.mkNot(c);
			solver.add(c);
		}
		
		/* declare edit-dist: Q x \Sigma -> Z */
		Sort[] argsToEd = new Sort[]{ I, I };
		FuncDecl<Sort> edDist = ctx.mkFuncDecl("ed_dist", argsToEd, I);
		
		/* declare C: Q -> Z */
		Sort[] argsToC = new Sort[]{ I, I, I };
		FuncDecl energy = ctx.mkFuncDecl("C", argsToC, I);
		
		/* C(q^0_R, q^0, q^0_T) = 0 */
		solver.add(ctx.mkEq(energy.apply(zero, zero, zero), zero));
		
		
		/* edit-distance constraints */
		
		for (int i = 0; i < numStates; i++) {	// q 
			Expr<IntSort> q = ctx.mkInt(i);
				
			for (SFAMove<CharPred, Character> sourceTransition : sourceTransitions) {
				Integer stateFrom = sourceTransition.from;
				Character move = sourceTransition.getWitness(ba);
				Expr<IntSort> qR = ctx.mkInt(stateFrom);
				Expr<IntSort> a = ctx.mkInt(alphabetMap.get(move));
				
				/* make variable out_len(q, a) */
				Expr outLenExpr = out_len.apply(q, a);
				
				/* make variable ed_dist(q, a) */
				Expr edDistExpr = edDist.apply(q, a);
				
				/* c_0 = d1(q, a, 0), c_1 = d1(q, a, 1), ..., c_{l-1} = d1(q, a, l-1) */
				
				/* make array of output chars */
				Expr[] outputChars = new Expr[length];
				
				/* comparing a to each output char */
				Expr disjunct = ctx.mkFalse();
				
				for (int l = 0; l < length; l++) {
					Expr<IntSort> index = ctx.mkInt(l);
					Expr d1exp = d1.apply(q, a, index);
					outputChars[l] = d1exp;
					Expr lt = ctx.mkLt(index, outLenExpr);
					Expr eq = ctx.mkEq(a, d1exp);
					disjunct = ctx.mkOr(disjunct, ctx.mkAnd(lt, eq));
				}

				/* for condition where the output chars don't include 'a' */
				Expr negDisjunct = ctx.mkNot(disjunct);
				
				/* (k = 0) ==> ed_dist(q, a) = 1 */
				Expr lenEq = ctx.mkEq(outLenExpr, zero);
				Expr edDistEqOne = ctx.mkEq(edDistExpr, ctx.mkInt(1));
				Expr impl1 = ctx.mkImplies(lenEq, edDistEqOne);
				
				/* \neg (k = 0) ==> ed_dist(q, a) = k - 1 */
				Expr lenNotZero = ctx.mkNot(lenEq);
				Expr edDistKMinus1 = ctx.mkEq(edDistExpr, ctx.mkSub(outLenExpr, ctx.mkInt(1))); 	
				Expr impl2 = ctx.mkImplies(lenNotZero, edDistKMinus1);
				
				/* \neg (k = 0) ==> ed_dist(q, a) = k */
				Expr edDistK = ctx.mkEq(edDistExpr, outLenExpr); 
				Expr impl3 = ctx.mkImplies(lenNotZero, edDistK);
				
				/* ed_dist constraint 1 */
				Expr consequent = ctx.mkAnd(impl1, impl2);
				solver.add(ctx.mkImplies(disjunct, consequent));
					
				/* ed_dist constraint 2 */
				consequent = ctx.mkAnd(impl1, impl3);
				solver.add(ctx.mkImplies(negDisjunct, consequent));
			}
		}
		
		
		for (int i = 0; i < numStates; i++) {	// q 
			Expr<IntSort> q = ctx.mkInt(i);
				
			for (SFAMove<CharPred, Character> sourceTransition : sourceTransitions) {
				Integer stateFrom = sourceTransition.from;
				Character move = sourceTransition.getWitness(ba);
				Expr<IntSort> qR = ctx.mkInt(stateFrom);
				Expr<IntSort> a = ctx.mkInt(alphabetMap.get(move));
				
				/* out_len(q, a) */
				Expr outLenExpr = out_len.apply(q, a);
					
				/* make variable q_R' = d_R(q_R, a), the equality is already encoded */
				Expr qRPrime = dR.apply(qR, a);
				
				
				/* make variable q' = d2(q, a) */
				Expr qPrime = d2.apply(q, a);
							
				
				/* c_0 = d1(q, a, 0), c_1 = d1(q, a, 1), ..., c_{l-1} = d1(q, a, l-1) */
				
				/* make array of output chars */
				Expr[] outputChars = new Expr[length];
				
				for (int l = 0; l < length; l++) {
					Expr<IntSort> index = ctx.mkInt(l);
					Expr d1exp = d1.apply(q, a, index);
					outputChars[l] = d1exp; 
				}
				
				/* ed_dist(q, a) */
				Expr edDistExpr = edDist.apply(q, a);
				
				/* m - (n x ed_dist(q, a)) */
				Expr<IntSort> m = ctx.mkInt(fraction[0]);
				Expr<IntSort> n = ctx.mkInt(fraction[1]);
				Expr diff = ctx.mkSub(m, ctx.mkMul(n, edDistExpr));
				
				for (Integer targetFrom : target.getStates()) {
					Expr<IntSort> qT = ctx.mkInt(targetFrom);
					
					
					/* q1 = dT(qT, c0), q2 = dT(q1, c1), ..., q_l = dT(q_{l-1}, c_{l-1}) */
					
					/* make array of destination states in target */
					Expr[] dstStates = new Expr[length];
					
					dstStates[0] = dT.apply(qT, outputChars[0]);
					for (int l = 1; l < length; l++) { 		// start from 1 in the loop
						dstStates[l] = dT.apply(dstStates[l - 1], outputChars[l]); // changed to l from l-1
					}
					
					
					/* x(q_R, q, q_T) */
					Expr xExpr = x.apply(qR, q, qT);
		
					/* C(q_R, q, q_T) */
					Expr cExpr = energy.apply(qR, q, qT);
					
					/* expressions for implications: out_len(q, a) = 0 ==> 
					 * x(qR', q', qT) /\ C(q_R, q, q_T) >= C(qRPrime, qPrime, qT) - diff */
					
					/* special case for 0 */
					Expr lenEq = ctx.mkEq(outLenExpr, zero);
					Expr xExprPrime = x.apply(qRPrime, qPrime, qT);
					
					/* C(q_R, q, q_T) >= C(qRPrime, qPrime, qT) - diff */
					Expr cExprPrime = energy.apply(qRPrime, qPrime, qT);
					Expr cGreaterExpr = ctx.mkGe(cExpr, ctx.mkSub(cExprPrime, diff));
					
					Expr c = ctx.mkImplies(lenEq, ctx.mkAnd(xExprPrime, cGreaterExpr));
					
					
					/* loop for the rest */
					Expr consequent = c;
					for (int l = 0; l < length; l++) {
						int outputLength = l + 1;
						lenEq = ctx.mkEq(outLenExpr, ctx.mkInt(outputLength));
						xExprPrime = x.apply(qRPrime, qPrime, dstStates[l]);
						
						cExprPrime = energy.apply(qRPrime, qPrime, dstStates[l]);
						cGreaterExpr = ctx.mkGe(cExpr, ctx.mkSub(cExprPrime, diff));
						
						c = ctx.mkImplies(lenEq, ctx.mkAnd(xExprPrime, cGreaterExpr));
						consequent = ctx.mkAnd(consequent, c);
					}
					
					/* make big constraint */
					solver.add(ctx.mkImplies(xExpr, consequent));
				}
			}
		}
		
		/* x(q_R, q, q_T) /\ f_R(q_R) -> f_T(q_T) /\ (C(q_R, q, q_T) >= 0) */
		for (int i = 0; i < numStates; i++) {
			for (Integer sourceState : source.getStates()) {
				for (Integer targetState : target.getStates()) {
					Expr<IntSort> sourceInt = ctx.mkInt(sourceState);
					Expr<IntSort> stateInt = ctx.mkInt(i);
					Expr<IntSort> targetInt = ctx.mkInt(targetState);
					
					Expr xExpr = x.apply(sourceInt, stateInt, targetInt);
					Expr fRExp = f_R.apply(sourceInt);
					Expr antecedent = ctx.mkAnd(xExpr, fRExp);
					
					Expr cExpr = energy.apply(sourceInt, stateInt, targetInt);
					Expr cGreaterExp = ctx.mkGe(cExpr, zero);
					Expr fTExp = f_T.apply(targetInt);
					Expr consequent = ctx.mkAnd(fTExp, cGreaterExp);
					
					Expr c = ctx.mkImplies(antecedent, consequent);
					solver.add(c);
				}
			}
		}
		
		
		
		/* Integer Pair datatype */
		TupleSort pair = ctx.mkTupleSort(ctx.mkSymbol("mkPair"), // name of tuple constructor
				 							new Symbol[] { ctx.mkSymbol("first"), ctx.mkSymbol("second") }, // names of projection operators
				 							new Sort[] { I, I } // types of projection operators
				 						);
		FuncDecl first = pair.getFieldDecls()[0]; // declarations are for projections
		FuncDecl second = pair.getFieldDecls()[1];
		
		/* example constraints */
		FuncDecl[] eFuncs = new FuncDecl[ioExamples.size()];
		
		int exampleCount = 0;
		for (Pair<String, String> ioExample : ioExamples) {
			/* verify example */
			if (!SFAOperations.isAcceptedBy(ioExample.first, source, ba)) throw new IllegalArgumentException();
			if (!SFAOperations.isAcceptedBy(ioExample.second, target, ba)) throw new IllegalArgumentException();
			
			int[] inputArr = stringToIntArray(alphabetMap, ioExample.first);
			int[] outputArr = stringToIntArray(alphabetMap, ioExample.second);
			
			/* declare function e_k: k x input_position -> (output_position, Q) */
			Sort[] args = new Sort[] {I};
			eFuncs[exampleCount] = ctx.mkFuncDecl("e " + String.valueOf(exampleCount), args, pair);
			FuncDecl e = eFuncs[exampleCount];
			
			/* initial position : e_k(0) = (0, q_0) */
			Expr initPair = pair.mkDecl().apply(zero, zero);
			solver.add(ctx.mkEq(e.apply(zero), initPair));
			
			int inputLen = ioExample.first.length();
			Expr<IntSort> inputLength = ctx.mkInt(inputLen);
			int outputLen = ioExample.second.length();
			Expr<IntSort> outputLength = ctx.mkInt(outputLen);
			
			/* 0 <= e_k(l1).first <= outputLen and 0 <= e_k(l1).second < numStates */
			for (int l = 0; l <= inputLen; l++) {
					Expr eExpr = e.apply(ctx.mkInt(l));
					Expr eExprFirst = first.apply(eExpr);
					Expr eExprSecond = second.apply(eExpr);
					
					/* restrict values of first */
					solver.add(ctx.mkLe(zero, eExprFirst));
					solver.add(ctx.mkLe(eExprFirst, outputLength));
					
					/* restrict values of second */
					solver.add(ctx.mkLe(zero, eExprSecond));
					solver.add(ctx.mkLt(eExprSecond, numStatesInt));
			}
			
			/* final position : e_k(l1).first = l2 */
			Expr eExprFirst = first.apply(e.apply(inputLength));
			solver.add(ctx.mkEq(eExprFirst, outputLength));
	
			
			for (int s = 0; s < numStates; s++) {	// q 
				Expr<IntSort> q = ctx.mkInt(s);
					
				for (SFAMove<CharPred, Character> sourceTransition : sourceTransitions) {
					Integer stateFrom = sourceTransition.from;
					Character move = sourceTransition.getWitness(ba);
					Expr<IntSort> qR = ctx.mkInt(stateFrom);
					Expr<IntSort> a = ctx.mkInt(alphabetMap.get(move));
					
					/* out_len(q, a) */
					Expr outLenExpr = out_len.apply(q, a);
						
					/* make variable q_R' = d_R(q_R, a), the equality is already encoded */
					Expr qRPrime = dR.apply(qR, a);
					
					
					/* make variable q' = d2(q, a) */
					Expr qPrime = d2.apply(q, a);
								
					
					/* c_0 = d1(q, a, 0), c_1 = d1(q, a, 1), ..., c_{l-1} = d1(q, a, l-1) */
					
					/* make array of output chars */
					Expr[] outputChars = new Expr[length];
					
					for (int l = 0; l < length; l++) {
						Expr<IntSort> index = ctx.mkInt(l);
						Expr d1exp = d1.apply(q, a, index);
						outputChars[l] = d1exp;
					}
					
					/* ed_dist(q, a) */
					Expr edDistExpr = edDist.apply(q, a);
					
					/* m - (n x ed_dist(q, a)) */
					Expr<IntSort> m = ctx.mkInt(fraction[0]);
					Expr<IntSort> n = ctx.mkInt(fraction[1]);
					Expr diff = ctx.mkSub(m, ctx.mkMul(n, edDistExpr));
					
					for (Integer targetFrom : target.getStates()) {
						Expr<IntSort> qT = ctx.mkInt(targetFrom);
						
						/* q1 = dT(qT, c0), q2 = dT(q1, c1), ..., q_l = dT(q_{l-1}, c_{l-1}) */
						
						/* make array of destination states in target */
						Expr[] dstStates = new Expr[length];
						
						dstStates[0] = dT.apply(qT, outputChars[0]);
						for (int l = 1; l < length; l++) { 		// start from 1 in the loop
							dstStates[l] = dT.apply(dstStates[l - 1], outputChars[l]);
						}
						
						/* x(q_R, q, q_T) */
						Expr xExpr = x.apply(qR, q, qT);
						
						/* C(q_R, q, q_T) */
						Expr cExpr = energy.apply(qR, q, qT);
						
						for (int i = 0; i < inputLen; i++) { 	// rationale: always read an input character, it's fine to have transition that reads last input char, 
							for (int j = 0; j <= outputLen; j++) {	// but output is already completely generated
								Expr<IntSort> inputPosition = ctx.mkInt(i);
								Expr<IntSort> outputPosition = ctx.mkInt(j);
								
								/* input[i+1] = a */
								Expr nextInputPosition = ctx.mkInt(inputArr[i]);
								Expr inputEq = ctx.mkEq(nextInputPosition, a);
								
								/* output needs be <= outputLen - j */
								int possibleOutputLen = Math.min(outputLen - j, length);
								Expr possibleOutputLength = ctx.mkInt(possibleOutputLen);
								
								Expr outputLe = ctx.mkLe(outLenExpr, possibleOutputLength);
								
								/* e_k(i) = (j, q) */
								Expr eExpr = ctx.mkEq(e.apply(inputPosition), pair.mkDecl().apply(outputPosition, q));
								
								/* expressions for implications: out_len(q, a) = 0 ==> e_k(i+1) = (j, q') 
								 * /\ x(qR', q', qT) /\ C(q_R, q, q_T) >= C(qRPrime, qPrime, qT) - diff */
								
								/* special case for 0 */
								Expr lenEq = ctx.mkEq(outLenExpr, zero);
								Expr eExprPrime = ctx.mkEq(e.apply(ctx.mkInt(i + 1)), pair.mkDecl().apply(outputPosition, qPrime));
								Expr xExprPrime = x.apply(qRPrime, qPrime, qT);
								
								/* C(q_R, q, q_T) >= C(qRPrime, qPrime, qT) - diff */
								Expr cExprPrime = energy.apply(qRPrime, qPrime, qT);
								Expr cGreaterExpr = ctx.mkGe(cExpr, ctx.mkSub(cExprPrime, diff));
								
								Expr c = ctx.mkImplies(lenEq, ctx.mkAnd(eExprPrime, xExprPrime, cGreaterExpr));
								
								/* loop for the rest */
								Expr consequent = ctx.mkAnd(outputLe, c);
								for (int l = 0; l < possibleOutputLen; l++) { 
									int outputGenLength = l + 1;
									lenEq = ctx.mkEq(outLenExpr, ctx.mkInt(outputGenLength));
									eExprPrime = ctx.mkEq(e.apply(ctx.mkInt(i + 1)), pair.mkDecl().apply(ctx.mkInt(j + outputGenLength), qPrime));
									xExprPrime = x.apply(qRPrime, qPrime, dstStates[l]);
									
									cExprPrime = energy.apply(qRPrime, qPrime, dstStates[l]);
									cGreaterExpr = ctx.mkGe(cExpr, ctx.mkSub(cExprPrime, diff));
									
									/* equalities */
									Expr stringEqualities = ctx.mkTrue();
									for (int inc = 1; inc <= outputGenLength; inc++) {
										int index = (j + inc) - 1;
										Expr nextPosition = ctx.mkInt(outputArr[index]);
										Expr eq = ctx.mkEq(nextPosition, outputChars[inc - 1]); 	// not sure about this
										stringEqualities = ctx.mkAnd(stringEqualities, eq);
									}
									
									c = ctx.mkImplies(lenEq, ctx.mkAnd(stringEqualities, eExprPrime, xExprPrime, cGreaterExpr)); 
									consequent = ctx.mkAnd(consequent, c);
								}
								
								
								/* make big constraint */
								Expr antecedent = ctx.mkAnd(eExpr, xExpr, inputEq);
								solver.add(ctx.mkImplies(antecedent, consequent));
							}
						}
						
					}
					
				}
			}
			exampleCount++;
		}
		
		/* use the d2 relation (the successor states) of the template, if one is provided, and enforce it */
		if (template != null) {
			for (SFAMove<CharPred, Character> transition : template.getTransitions()) { 	
				Integer stateFrom = transition.from;
				Character move = transition.getWitness(ba);
				Integer stateTo = transition.to;
				
				Expr<IntSort> q = ctx.mkInt(stateFrom);
				Expr<IntSort> a = ctx.mkInt(alphabetMap.get(move));
				Expr qPrime = ctx.mkInt(stateTo);
				
				solver.add(ctx.mkEq(d2.apply(q, a), qPrime));
			}
		}
		
		
		
		
		/* Debugging: enforce desired constraints */
		Expr intTwo = ctx.mkInt(2);
		Expr intOne = ctx.mkInt(1);
		Expr intFive = ctx.mkInt(5);
		Expr intThree = ctx.mkInt(3);
		Expr intFour = ctx.mkInt(4);
		Expr intSix = ctx.mkInt(6);
//		solver.add(ctx.mkEq(d1.apply(zero, zero, zero), intOne));
//		solver.add(ctx.mkEq(d2.apply(zero, zero), (Expr) zero));
//		solver.add(ctx.mkEq(out_len.apply(zero, zero), (Expr) zero));
//		solver.add(ctx.mkEq(energy.apply(intOne, zero, zero), ctx.mkInt(-1)));
		
		
		
		/* Print SMT string to smtFile */
		try {
			if (smtFile != null) {
				BufferedWriter br = new BufferedWriter(new FileWriter(new File(smtFile)));
				br.write(solver.toString());
				br.write("(check-sat)");
				br.close();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		
		/* Reconstruct transducer */
		
		HashMap<Integer, Character> revAlphabetMap = reverseMap(alphabetMap);
		
		Set<SFTMove<CharPred, CharFunc, Character>> transitionsFT = new HashSet<SFTMove<CharPred, CharFunc, Character>>();
		
		long startTime = System.nanoTime();
		if (solver.check() == Status.SATISFIABLE) {
			Model m = solver.getModel();
			long stopTime = System.nanoTime();
			System.out.println((stopTime - startTime));
			System.out.println((stopTime - startTime) / 1000000000);
			
			/* Debug */
			if (debug) { 
				System.out.println(solver.toString());
				
				/* d1 and d2 */	
				for (int q1 = 0; q1 < numStates; q1++) {
					Expr state = ctx.mkInt(q1);
					
					for (int move : alphabetMap.values())  { 
						Character input = revAlphabetMap.get(move);
						Expr a = ctx.mkInt(move); 
						
						/* get state to */
						Expr d2exp = d2.apply(state, a);
						int q2 = ((IntNum) m.evaluate(d2exp, false)).getInt();
						
						/* output_len */
						Expr outputLenExpr = out_len.apply(state, a);
						int outputLen = ((IntNum) m.evaluate(outputLenExpr, false)).getInt();
						
						/* get output */
						StringBuilder outputStr = new StringBuilder("");
						for (int i = 0; i < outputLen; i++) {
							Expr<IntSort> index = ctx.mkInt(i);
							Expr d1exp = d1.apply(state, a, index);
							int outMove = ((IntNum) m.evaluate(d1exp, false)).getInt();
							Character output = revAlphabetMap.get(outMove);
							outputStr.append(output);
						}
						
						/* print d1, d2 combined for convenience */
						System.out.println("d(" + q1 + ", " + input + ", " + outputStr + ", " + q2 + ")");
						
						/* edit-distance of transitions */
						Expr edDistExpr = edDist.apply(state, a);
						int editDist = ((IntNum) m.evaluate(edDistExpr, false)).getInt();
						System.out.println("edit-distance(" + q1 + ", " + input + ", " + outputStr + ") = " + editDist);
					}
				}
					
				/* values for which x(q_R, q, q_T) is set to TRUE and corresponding values of C(q_R, q, q_T) */
				for (int i = 0; i < numStates; i++) {
					for (Integer sourceState : source.getStates()) {
						for (Integer targetState : target.getStates()) {
							Expr<IntSort> sourceInt = ctx.mkInt(sourceState);
							Expr<IntSort> stateInt = ctx.mkInt(i);
							Expr<IntSort> targetInt = ctx.mkInt(targetState);
								
							Expr exp1 = x.apply(sourceInt, stateInt, targetInt);
							Expr exp2 = energy.apply(sourceInt, stateInt, targetInt);
							if (m.evaluate(exp1, false).isTrue()) {
								System.out.println("x(" + sourceState + ", " + stateInt + ", " + targetState + ")");
								int energyVal = ((IntNum) m.evaluate(exp2, false)).getInt();
								System.out.println("C(" + sourceState + ", " + stateInt + ", " + targetState + ")" + " = " + energyVal);
							}
							
							
						}
					}
				}
					
				/* values of e(i) */
				exampleCount = 0;
				for (Pair<String, String> example : ioExamples) {
					int inputLen = example.first.length();
					System.out.println(example.first + " --> " + example.second);
						
					FuncDecl e = eFuncs[exampleCount];
							
					for (int i = 0; i <= inputLen; i++) {
						Expr eExpr = e.apply(ctx.mkInt(i));
						Expr eExprFirst = first.apply(eExpr);
						Expr eExprSecond = second.apply(eExpr);
						
						int j = ((IntNum) m.evaluate(eExprFirst, false)).getInt();
						int state = ((IntNum) m.evaluate(eExprSecond, false)).getInt();
						String inputStr = example.first.substring(0, i);
						String outputStr = example.second.substring(0, j);
						System.out.println("e_" + exampleCount + "(" + inputStr + ", " + outputStr + ", " + state + ")");
					}
					exampleCount++;
				}
		    }
			
			/* Add transitions to FT */
			if (template != null) {
				/* Only add 'relevant' transitions */
				for (SFAMove<CharPred, Character> transition : template.getTransitions()) { 	
					Integer stateFrom = transition.from;
					Character move = transition.getWitness(ba);
					Integer stateTo = transition.to;
					
					Expr<IntSort> q1 = ctx.mkInt(stateFrom);
					Expr<IntSort> a = ctx.mkInt(alphabetMap.get(move));
					Expr q2 = ctx.mkInt(stateTo);
					
					/* output_len */
					Expr outputLenExpr = out_len.apply(q1, a);
					int outputLen = ((IntNum) m.evaluate(outputLenExpr, false)).getInt();
								
					/* get output */
					List<CharFunc> outputFunc = new ArrayList<CharFunc>();
					for (int i = 0; i < outputLen; i++) {
						Expr<IntSort> index = ctx.mkInt(i);
						Expr d1exp = d1.apply(q1, a, index);
						int outMove = ((IntNum) m.evaluate(d1exp, false)).getInt();
						Character output = revAlphabetMap.get(outMove);
						outputFunc.add(new CharConstant(output));
					}
								
					SFTInputMove<CharPred, CharFunc, Character> newTrans = new SFTInputMove<CharPred, CharFunc, Character>(stateFrom, stateTo, new CharPred(move), outputFunc);
					transitionsFT.add(newTrans);
				}
				
			} else {
				for (int q1 = 0; q1 < numStates; q1++) {
					for (int move : alphabetMap.values())  { 
						Character input = revAlphabetMap.get(move);
						Expr state = ctx.mkInt(q1);
						Expr a = ctx.mkInt(move); 
						
						/* get state to */
						Expr d2exp = d2.apply(state, a);
						int q2 = ((IntNum) m.evaluate(d2exp, false)).getInt();
									
						/* output_len */
						Expr outputLenExpr = out_len.apply(state, a);
						int outputLen = ((IntNum) m.evaluate(outputLenExpr, false)).getInt();
									
						/* get output */
						List<CharFunc> outputFunc = new ArrayList<CharFunc>();
						for (int i = 0; i < outputLen; i++) {
							Expr<IntSort> index = ctx.mkInt(i);
							Expr d1exp = d1.apply(state, a, index);
							int outMove = ((IntNum) m.evaluate(d1exp, false)).getInt();
							Character output = revAlphabetMap.get(outMove);
							outputFunc.add(new CharConstant(output));
						}
									
						SFTInputMove<CharPred, CharFunc, Character> newTrans = new SFTInputMove<CharPred, CharFunc, Character>(q1, q2, new CharPred(input), outputFunc);
						transitionsFT.add(newTrans);
					}
				}
			}
					
		} else {
			long stopTime = System.nanoTime();
			System.out.println((stopTime - startTime));
			System.out.println((stopTime - startTime) / 1000000000);
		}
		
		HashMap<Integer, Set<List<Character>>> finStates = new HashMap<Integer, Set<List<Character>>>();
		SFT<CharPred, CharFunc, Character> mySFT = SFT.MkSFT(transitionsFT, 0, finStates, ba);
		
		return mySFT;
		
	}
		
}



