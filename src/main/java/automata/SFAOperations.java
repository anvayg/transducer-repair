package automata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sat4j.specs.TimeoutException;

import automata.sfa.SFA;
import automata.sfa.SFAInputMove;
import automata.sfa.SFAMove;
import theory.BooleanAlgebra;
import theory.characters.CharPred;
import utilities.Pair;
import utilities.Triple;

/**
 * Class for functions/operations on SFA over characters
 * 
 * @author anvaygrover
 *
 */
public class SFAOperations {
	
	public static Integer getSuccessorState(SFA<CharPred, Character> aut, Integer state, Character move, 
			BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		Collection<SFAMove<CharPred, Character>> transitions = aut.getTransitionsFrom(state);
		
		// assumes disjoint and total set of transitions (minterm reduced)
		for (SFAMove<CharPred, Character> transition : transitions) {
			if (transition.hasModel(move, ba)) {
				return transition.to;
			}
		}
		
		// should be unreachable if total
		return -1;
	}
	
	/*
	 * Similar to getSuccessorState but check whether transition is present
	 */
	public static boolean hasTransition(SFA<CharPred, Character> aut, Integer state, Character move, 
			BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		Collection<SFAMove<CharPred, Character>> transitions = aut.getTransitionsFrom(state);
		
		// assumes disjoint and total set of transitions (minterm reduced)
		for (SFAMove<CharPred, Character> transition : transitions) {
			if (transition.hasModel(move, ba)) {
				return true;
			}
		}
		
		return false;
	}
	
	/*
	 * Returns state of aut after reading String str
	 */
	public static Integer getStateInFA(SFA<CharPred, Character> aut, Integer state, String str, 
			BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		for (int i = 0; i < str.length(); i++) {
			Character move = str.charAt(i);
			state = getSuccessorState(aut, state, move, ba);
		}
		
		return state;
	}
	
	/*
	 * Returns all positions in 'str' such that aut is in state 'state' while reading 'str'
	 */
	public static List<Integer> getPositionInStr(SFA<CharPred, Character> aut, Integer state, String str, 
			BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		List<Integer> positions = new LinkedList<Integer>();
		Integer currentState = 0;
		if (currentState == state) positions.add(0);
		
		for (int i = 0; i < str.length(); i++) {
			Character move = str.charAt(i);
			currentState = getSuccessorState(aut, currentState, move, ba);
			if (currentState == state) positions.add(i + 1); 	// check for possible off by 1
		}
		
		return positions;
	}
	
	// Assume aut has transitions labeled with single characters
	public static HashSet<Character> alphabetSet(SFA<CharPred, Character> aut, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		HashSet<Character> alphabet = new HashSet<Character>();
		Collection<SFAMove<CharPred, Character>> transitions1 = aut.getTransitions();
		
		for (SFAMove<CharPred, Character> transition : transitions1) {
			Character label = transition.getWitness(ba);
			alphabet.add(label);
		}
		
		// alphabet.add(Character.MIN_VALUE); // use for empty, removing for now!
		return alphabet;
	}
	
	public static HashSet<Character> alphabetSetMult(SFA<CharPred, Character> aut1, SFA<CharPred, Character> aut2, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		HashSet<Character> alphabet = alphabetSet(aut1, ba);
		alphabet.addAll(alphabetSet(aut2, ba));
		
		return alphabet;
	}
	
	public static HashMap<Character, Integer> mkAlphabetMap(Set<Character> alphabet) {
		HashMap<Character, Integer> alphabetMap = new HashMap<Character, Integer>();
		int counter = 0;
		
		for (Character sym : alphabet) {
			alphabetMap.put(sym, counter);
			counter++;
		}
		
		return alphabetMap;
	}
	
	/* 
	 * Makes aut a total finite automaton using the given alphabet set
	 */
	public static SFA<CharPred, Character> mkTotalFinite(SFA<CharPred, Character> aut, Set<Character> alphabet, 
			BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		Collection<SFAMove<CharPred, Character>> transitions = new ArrayList<SFAMove<CharPred, Character>>();
		int sinkState = aut.getMaxStateId() + 1;
		transitions.addAll(aut.getTransitions());
		
		for (Integer state : aut.getStates()) {
			for (Character c : alphabet) {
				if (!hasTransition(aut, state, c, ba)) {
					transitions.add(new SFAInputMove<CharPred, Character>(state, sinkState, ba.MkAtom(c)));
				}
			}
		}
		
		// self-loop on sinkState
		for (Character c : alphabet) {
			transitions.add(new SFAInputMove<CharPred, Character>(sinkState, sinkState, ba.MkAtom(c)));
		}
		
		return SFA.MkSFA(transitions, aut.getInitialState(), aut.getFinalStates(), ba, false, false);
	}
	
	/* 
	 * Returns true if a run of string on aut ends in a final state
	 */
	public static boolean isAcceptedBy(String str, SFA<CharPred, Character> aut, 
			BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		Integer state = getStateInFA(aut, 0, str, ba);
		if (aut.isFinalState(state)) return true;
		
		return false;
	}
	
	/**
	 * Make finite automata from the minterms of both aut1 and aut2
	 * 
	 * @throws TimeoutException
	 */
	@SuppressWarnings("unchecked")
	public static Triple<SFA<CharPred, Character>, SFA<CharPred, Character>, Map<CharPred, Pair<CharPred, ArrayList<Integer>>>>
	MkFiniteSFA(SFA<CharPred, Character> aut1, SFA<CharPred, Character> aut2, BooleanAlgebra<CharPred, Character> ba) 
			throws TimeoutException {
		
		// Get all predicates
		ArrayList<CharPred> predicates1 = new ArrayList<CharPred>(); 
		ArrayList<CharPred> predicates2 = new ArrayList<CharPred>(); 
		 
		for (Integer state : aut1.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut1.getInputMovesFrom(state)) {
				predicates1.add(transition.guard);
			}
		}
		
		for (Integer state : aut2.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut2.getInputMovesFrom(state)) {
				predicates2.add(transition.guard);
			}
		}
		
		predicates1.addAll(predicates2);
		
		// Get minterms
		Collection<Pair<CharPred, ArrayList<Integer>>> minterms = ba.GetMinterms(predicates1);
		Map<Pair<CharPred, ArrayList<Integer>>, CharPred> mintermToId = new HashMap<Pair<CharPred, ArrayList<Integer>>, CharPred>();
		Map<CharPred, Pair<CharPred, ArrayList<Integer>>> idToMinterm = new HashMap<CharPred, Pair<CharPred, ArrayList<Integer>>>();
		for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
			CharPred newPred = ba.MkAtom(ba.generateWitness(minterm.first));
			mintermToId.put(minterm, newPred);
			idToMinterm.put(newPred, minterm);
		}
		
		// Make new transitions
		Collection<SFAMove<CharPred, Character>> transitions1 = new ArrayList<SFAMove<CharPred, Character>>();
		Collection<SFAMove<CharPred, Character>> transitions2 = new ArrayList<SFAMove<CharPred, Character>>();
		
		for (Integer state : aut1.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut1.getInputMovesFrom(state)) {
				for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
					CharPred conj = ba.MkAnd(transition.guard, minterm.first);
					if (ba.IsSatisfiable(conj)) {
						SFAInputMove<CharPred, Character> newTransition = (SFAInputMove<CharPred, Character>) transition.clone();
						newTransition.guard = mintermToId.get(minterm);
						transitions1.add(newTransition);
					}
				}
			}
		}
		
		SFA<CharPred, Character> finAut1 = SFA.MkSFA(transitions1, aut1.getInitialState(), aut1.getFinalStates(), ba, false, false);
		
		for (Integer state : aut2.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut2.getInputMovesFrom(state)) {
				for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
					CharPred conj = ba.MkAnd(transition.guard, minterm.first);
					if (ba.IsSatisfiable(conj)) {
						SFAInputMove<CharPred, Character> newTransition = (SFAInputMove<CharPred, Character>) transition.clone();
						newTransition.guard = mintermToId.get(minterm);
						transitions2.add(newTransition);
					}
				}
			}
		}
		
		SFA<CharPred, Character> finAut2 = SFA.MkSFA(transitions2, aut2.getInitialState(), aut2.getFinalStates(), ba, false, false);
		
		return new Triple<SFA<CharPred, Character>, SFA<CharPred, Character>, Map<CharPred, Pair<CharPred, ArrayList<Integer>>>>(finAut1, finAut2, idToMinterm);
	}
	
	/**
	 * Variant of MkFiniteSFA that optionally accepts minterms
	 * 
	 * @param aut1
	 * @param ba
	 * @return
	 * @throws TimeoutException 
	 */
	public static SFA<CharPred, Character> MkFiniteSFA(SFA<CharPred, Character> aut1, 
			Collection<Pair<CharPred, ArrayList<Integer>>> minterms, 
			Map<Pair<CharPred, ArrayList<Integer>>, CharPred> mintermToId, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		// Make new transitions
		Collection<SFAMove<CharPred, Character>> transitions1 = new ArrayList<SFAMove<CharPred, Character>>();

		for (Integer state : aut1.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut1.getInputMovesFrom(state)) {
				for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
					CharPred conj = ba.MkAnd(transition.guard, minterm.first);
					if (ba.IsSatisfiable(conj)) {
						SFAInputMove<CharPred, Character> newTransition = (SFAInputMove<CharPred, Character>) transition.clone();
						newTransition.guard = mintermToId.get(minterm);
						transitions1.add(newTransition);
					}
				}
			}
		}

		SFA<CharPred, Character> finAut1 = SFA.MkSFA(transitions1, aut1.getInitialState(), aut1.getFinalStates(), ba, false, false);
				
		return finAut1;
	}
	
	public static Pair<Map<Pair<CharPred, ArrayList<Integer>>, CharPred>, Map<CharPred, Pair<CharPred, ArrayList<Integer>>>> 
	constructMintermMap(Collection<Pair<CharPred, ArrayList<Integer>>> minterms, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		Map<Pair<CharPred, ArrayList<Integer>>, CharPred> mintermToId = new HashMap<Pair<CharPred, ArrayList<Integer>>, CharPred>();
		Map<CharPred, Pair<CharPred, ArrayList<Integer>>> idToMinterm = new HashMap<CharPred, Pair<CharPred, ArrayList<Integer>>>();
		for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
			CharPred newPred = ba.MkAtom(ba.generateWitness(minterm.first));
			mintermToId.put(minterm, newPred);
			idToMinterm.put(newPred, minterm);
		}
		
		return new Pair<Map<Pair<CharPred, ArrayList<Integer>>, CharPred>, Map<CharPred, Pair<CharPred, ArrayList<Integer>>>>(mintermToId, idToMinterm);
	}
	
	/* Calls mintermReduction, implemented in symbolicautomata */
	public static Triple<SFA<CharPred, Character>, SFA<CharPred, Character>, Map<CharPred, Pair<CharPred, ArrayList<Integer>>>> 
	mkFinite(SFA<CharPred, Character> aut1, SFA<CharPred, Character> aut2, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		return SFA.MkFiniteSFA(aut1, aut2, ba);
	}
	
	
	public static List<CharPred> getPreds(SFA<CharPred, Character> aut) {
		ArrayList<CharPred> predicates = new ArrayList<CharPred>(); 

		for (Integer state : aut.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut.getInputMovesFrom(state)) {
				predicates.add(transition.guard);
			}
		}
		
		return predicates;
	}
	
	public static Collection<Pair<CharPred, ArrayList<Integer>>> getMinterms(List<SFA<CharPred, Character>> auts, 
			BooleanAlgebra<CharPred, Character> ba) {
		// Get all predicates
		ArrayList<CharPred> predicates = new ArrayList<CharPred>(); 

		for (SFA<CharPred, Character> aut : auts) {
			for (Integer state : aut.getStates()) {
				for (SFAInputMove<CharPred, Character> transition : aut.getInputMovesFrom(state)) {
					predicates.add(transition.guard);
				}
			}
		}
		
		// Get minterms
		Collection<Pair<CharPred, ArrayList<Integer>>> minterms = ba.GetMinterms(predicates);
		
		return minterms;
	}
	
	public static Collection<Pair<CharPred, ArrayList<Integer>>> getMinterms(SFA<CharPred, Character> aut, 
			BooleanAlgebra<CharPred, Character> ba) {
		// Get all predicates
		ArrayList<CharPred> predicates = new ArrayList<CharPred>(); 

		for (Integer state : aut.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut.getInputMovesFrom(state)) {
				predicates.add(transition.guard);
			}
		}
		
		// Get minterms
		Collection<Pair<CharPred, ArrayList<Integer>>> minterms = ba.GetMinterms(predicates);
		
		return minterms;
	}
	
	public static SFA<CharPred, Character> unnormalize(SFA<CharPred, Character> aut1, 
			Collection<Pair<CharPred, ArrayList<Integer>>> minterms, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		if (minterms == null) {
			// Get all predicates
			ArrayList<CharPred> predicates1 = new ArrayList<CharPred>();  
			 
			for (Integer state : aut1.getStates()) {
				for (SFAInputMove<CharPred, Character> transition : aut1.getInputMovesFrom(state)) {
					predicates1.add(transition.guard);
				}
			}
			
			minterms = ba.GetMinterms(predicates1);
		}
		
		// Make new transitions
		Collection<SFAMove<CharPred, Character>> transitions1 = new ArrayList<SFAMove<CharPred, Character>>();
		
		for (Integer state : aut1.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut1.getInputMovesFrom(state)) {
				for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
					CharPred conj = ba.MkAnd(transition.guard, minterm.first);
					if (ba.IsSatisfiable(conj)) {
						SFAInputMove<CharPred, Character> newTransition = (SFAInputMove<CharPred, Character>) transition.clone();
						newTransition.guard = minterm.first;
						transitions1.add(newTransition);
					}
				}
			}
		}
		
		SFA<CharPred, Character> finAut1 = SFA.MkSFA(transitions1, aut1.getInitialState(), aut1.getFinalStates(), ba, false, false);
		
		return finAut1;
	}
	
	/* 'Unnormalizes' two SFAs using the given minterms */
	public static Pair<SFA<CharPred, Character>, SFA<CharPred, Character>> unnormalize(SFA<CharPred, Character> aut1, 
			SFA<CharPred, Character> aut2, Collection<Pair<CharPred, ArrayList<Integer>>> minterms, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		if (minterms == null) {
			// Get all predicates
			ArrayList<CharPred> predicates1 = new ArrayList<CharPred>(); 
			ArrayList<CharPred> predicates2 = new ArrayList<CharPred>(); 
			 
			for (Integer state : aut1.getStates()) {
				for (SFAInputMove<CharPred, Character> transition : aut1.getInputMovesFrom(state)) {
					predicates1.add(transition.guard);
				}
			}
			
			for (Integer state : aut2.getStates()) {
				for (SFAInputMove<CharPred, Character> transition : aut2.getInputMovesFrom(state)) {
					predicates2.add(transition.guard);
				}
			}
			
			predicates1.addAll(predicates2);
			
			minterms = ba.GetMinterms(predicates1);
		}
		
		
		// Make new transitions
		Collection<SFAMove<CharPred, Character>> transitions1 = new ArrayList<SFAMove<CharPred, Character>>();
		Collection<SFAMove<CharPred, Character>> transitions2 = new ArrayList<SFAMove<CharPred, Character>>();

		for (Integer state : aut1.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut1.getInputMovesFrom(state)) {
				for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
					CharPred conj = ba.MkAnd(transition.guard, minterm.first);
					if (ba.IsSatisfiable(conj)) {
						SFAInputMove<CharPred, Character> newTransition = (SFAInputMove<CharPred, Character>) transition.clone();
						newTransition.guard = minterm.first;
						transitions1.add(newTransition);
					}
				}
			}
		}
		
		SFA<CharPred, Character> finAut1 = SFA.MkSFA(transitions1, aut1.getInitialState(), aut1.getFinalStates(), ba, false, false);
		
		for (Integer state : aut2.getStates()) {
			for (SFAInputMove<CharPred, Character> transition : aut2.getInputMovesFrom(state)) {
				for (Pair<CharPred, ArrayList<Integer>> minterm : minterms) {
					CharPred conj = ba.MkAnd(transition.guard, minterm.first);
					if (ba.IsSatisfiable(conj)) {
						SFAInputMove<CharPred, Character> newTransition = (SFAInputMove<CharPred, Character>) transition.clone();
						newTransition.guard = minterm.first;
						transitions2.add(newTransition);
					}
				}
			}
		}
		
		SFA<CharPred, Character> finAut2 = SFA.MkSFA(transitions2, aut2.getInitialState(), aut2.getFinalStates(), ba, false, false);

		return new Pair<SFA<CharPred, Character>, SFA<CharPred, Character>>(finAut1, finAut2); 
	}
	
	public static Pair<CharPred, ArrayList<Integer>> findSatisfyingMinterm(Character c, Map<CharPred, Pair<CharPred, ArrayList<Integer>>> idToMinterm) throws TimeoutException {
		for (Map.Entry<CharPred, Pair<CharPred, ArrayList<Integer>>> entry : idToMinterm.entrySet()) {
			CharPred minterm = entry.getValue().first;
			
			if (minterm.isSatisfiedBy(c)) { 	// only 1 minterm should be satisfied, since they are disjoint
				 return entry.getValue();
			}
		}
		
		return null;
	}
	
	/* Reduce each char of string to its corresponding minterm */
	public static String finitizeStringMinterms(String str, Map<CharPred, Pair<CharPred, ArrayList<Integer>>> idToMinterm, 
			BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		StringBuilder newString = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char currentChar = str.charAt(i);
			
			for (Map.Entry<CharPred, Pair<CharPred, ArrayList<Integer>>> entry : idToMinterm.entrySet()) {
				CharPred minterm = entry.getValue().first;
				
				if (minterm.isSatisfiedBy(currentChar)) { 	// only 1 minterm should be satisfied, since they are disjoint
					newString.append(ba.generateWitness(entry.getKey())); 	// should only be 1 witness too
				}
			}
		}
		
		return newString.toString();
	}
	
	/* Transform aut such that there is <= 1 transition between any 2 states */
	public static SFA<CharPred, Character> pseudoNormalize(SFA<CharPred, Character> aut, BooleanAlgebra<CharPred, Character> ba) throws TimeoutException {
		Collection<Integer> states = aut.getStates();
		Set<SFAMove<CharPred, Character>> newTransitions = new HashSet<SFAMove<CharPred, Character>>();
		Set<Integer> finalStates = new HashSet<Integer>();
		finalStates.addAll(aut.getFinalStates());
		int nextState = aut.getMaxStateId() + 1;
		
		for (Integer state : states) {
			Set<Integer> visited = new HashSet<Integer>();
			
			for (SFAInputMove<CharPred, Character> transition : aut.getInputMovesFrom(state)) {
				Integer successor = transition.to;
				if (!visited.contains(successor)) {
					visited.add(successor);
					newTransitions.add(transition);
				} else {
					// already seen this state, need to redirect transition to new state
					newTransitions.add(new SFAInputMove<CharPred, Character>(state, nextState, transition.guard));
					
					// add transitions from new state to the successors of successor
					for (SFAInputMove<CharPred, Character> succTransition : aut.getInputMovesFrom(successor)) {
						if (succTransition.to == successor) {
							newTransitions.add(new SFAInputMove<CharPred, Character>(nextState, succTransition.to, succTransition.guard));
						} else {
							newTransitions.add(new SFAInputMove<CharPred, Character>(nextState, succTransition.to, succTransition.guard));
						}
					}
					
					// if successor is final, then new state should also be
					if (aut.isFinalState(successor)) finalStates.add(nextState);
					
					// increment nextState
					nextState++;
				}
			}
			
		}
		
		return SFA.MkSFA(newTransitions, aut.getInitialState(), finalStates, ba, false, false);
	}
	
}
