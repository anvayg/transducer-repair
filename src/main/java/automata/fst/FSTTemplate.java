package automata.fst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import org.sat4j.specs.TimeoutException;

import automata.SFTOperations;
import theory.characters.CharFunc;
import theory.characters.CharPred;
import transducers.sft.SFT;
import transducers.sft.SFTInputMove;
import utilities.Pair;

public class FSTTemplate {
	/* Instance variables */
	private FST<Character, Character> aut;
	private Collection<FSTMove<Character, Character>> goodTransitions;
	private Collection<FSTMove<Character, Character>> badTransitions;
	
	public FSTTemplate(FST<Character, Character> aut, 
			Collection<FSTMove<Character, Character>> badTransitions) {
		this.aut = aut;
		this.badTransitions = badTransitions;
		
		Collection<FSTMove<Character, Character>> currentTransitions = new LinkedList<FSTMove<Character, Character>>();

		for (Integer state : aut.getStates()) {
			currentTransitions.addAll(aut.getTransitionsFrom(state));
		}
		
		currentTransitions.removeAll(badTransitions);
		this.goodTransitions = currentTransitions;
	}
	
	public FSTTemplate(SFT<CharPred, CharFunc, Character> aut, 
			Collection<SFTInputMove<CharPred, CharFunc, Character>> badTransitions,
			Collection<Pair<CharPred, ArrayList<Integer>>> minterms,
			Map<CharPred, Pair<CharPred, ArrayList<Integer>>> idToMinterm,
			Map<Pair<CharPred, ArrayList<Integer>>, CharPred> mintermToId) throws TimeoutException {
		FST<Character, Character> finAut = SFTOperations.mkFinite(aut, minterms, idToMinterm, mintermToId);
		this.aut = finAut;
		
		Collection<FSTMove<Character, Character>> finBadTransitions = 
				SFTOperations.mkTransitionsFinite(badTransitions, minterms, idToMinterm, mintermToId);
		this.badTransitions = finBadTransitions;
		
		Collection<FSTMove<Character, Character>> currentTransitions = new LinkedList<FSTMove<Character, Character>>();

		for (Integer state : finAut.getStates()) {
			currentTransitions.addAll(finAut.getTransitionsFrom(state));
		}
		
		currentTransitions.removeAll(finBadTransitions);
		this.goodTransitions = currentTransitions;
	}
	
	public FST<Character, Character> getAut() {
		return aut;
	}
	
	public Collection<FSTMove<Character, Character>> getGoodTransitions() {
		return goodTransitions;
	}
	
	public Collection<FSTMove<Character, Character>> getBadTransitions() {
		return badTransitions;
	}
	
}
