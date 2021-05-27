package benchmarks;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.sat4j.specs.TimeoutException;

import automata.SFAOperations;
import automata.sfa.SFA;
import solver.ConstraintsTestSymbolic;
import theory.characters.CharConstant;
import theory.characters.CharFunc;
import theory.characters.CharOffset;
import theory.characters.CharPred;
import theory.intervals.UnaryCharIntervalSolver;
import transducers.sft.SFT;
import transducers.sft.SFTInputMove;
import transducers.sft.SFTMove;
import utilities.Pair;
import utilities.SFAprovider;
import SFT.GetTag;
import SFT.MalwareFingerprintingDecode;

public class SFTBench {
	private static UnaryCharIntervalSolver ba = new UnaryCharIntervalSolver();
	
	/* Benchmarks from: https://www.doc.ic.ac.uk/~livshits/papers/pdf/popl12.pdf 
	 * Reference implementations: https://github.com/lorisdanto/symbolicautomata/blob/master/benchmarks/src/main/java/SFT/
	 * */
	
	/* QuicktimeSplitter */
	public static SFT<CharPred, CharFunc, Character> QuicktimeSplitter;
	
	
	public void quicktimeSplitter() throws TimeoutException {
		QuicktimeSplitter = MalwareFingerprintingDecode.MkQuicktimeSplitter();
		System.out.println(QuicktimeSplitter.toDotString(ba));
		System.out.println(QuicktimeSplitter.getFinalStatesAndTails());
		
		SFA<CharPred, Character> domain = QuicktimeSplitter.getDomain(ba).removeEpsilonMoves(ba);
		assertTrue(domain.accepts(lOfS("00#Quicktime7.6.9"), ba));
		assertTrue(domain.accepts(lOfS("0769#Quicktime7.6.9"), ba));
		
		SFA<CharPred, Character> range = QuicktimeSplitter.getOverapproxOutputSFA(ba).removeEpsilonMoves(ba).determinize(ba);
		
		int[] fraction = new int[] {1, 1};
		
		List<Pair<String, String>> examples = new ArrayList<Pair<String, String>>();
		examples.add(new Pair<String, String>("00#Quicktime7.6.9", "#769"));
		examples.add(new Pair<String, String>("0769#Quicktime7.6.9", "0769#"));
		
		ConstraintsTestSymbolic.customConstraintsTest(domain, range, 1, 1, fraction, examples, null, false);
	}
	
	/* QuicktimeMerger */
	public static SFT<CharPred, CharFunc, Character> QuicktimeMerger;
	
	
	public void quicktimeMerger() throws TimeoutException {
		QuicktimeMerger = MalwareFingerprintingDecode.MkQuicktimeMerger();
		System.out.println(QuicktimeMerger.toDotString(ba));
		SFA<CharPred, Character> domain = QuicktimeMerger.getDomain(ba).removeEpsilonMoves(ba);
		SFA<CharPred, Character> range = QuicktimeMerger.getOverapproxOutputSFA(ba).removeEpsilonMoves(ba).determinize(ba);
		
		String SOURCE_REGEX = "([#]?[0-9]+)|([0-9]+[#]?)";
		SFA<CharPred, Character> SOURCE = (new SFAprovider(SOURCE_REGEX, ba)).getSFA().removeEpsilonMoves(ba).determinize(ba);
		assertTrue(SOURCE.accepts(lOfS("#769"), ba));
		assertTrue(SOURCE.accepts(lOfS("769#"), ba));
		
		String TARGET_REGEX = "[0-9]+";
		SFA<CharPred, Character> TARGET = (new SFAprovider(TARGET_REGEX, ba)).getSFA().removeEpsilonMoves(ba).determinize(ba);
		assertTrue(TARGET.accepts(lOfS("769"), ba));
		
		int[] fraction = new int[] {1, 1};
		
		List<Pair<String, String>> examples = new ArrayList<Pair<String, String>>();
		examples.add(new Pair<String, String>("#769", "769"));
		examples.add(new Pair<String, String>("769#", "769"));
		
		ConstraintsTestSymbolic.customConstraintsTest(domain, range, 1, 1, fraction, examples, null, false);
		
		ConstraintsTestSymbolic.customConstraintsTest(SOURCE, TARGET, 1, 1, fraction, examples, null, false);
	}
	
	/* QuicktimePadder: requires memory? or non-determinism? */
	
	
	/* getTags -> deterministic */
	public static SFT<CharPred, CharFunc, Character> GetTags;
	
	/* Assume that there are no substrings of the form '<a' in the input, for experimenting */
	private static SFT<CharPred, CharFunc, Character> MkGetTagsSFTMod() throws TimeoutException {
		List<SFTMove<CharPred, CharFunc, Character>> transitions = new LinkedList<SFTMove<CharPred, CharFunc, Character>>();

		List<CharFunc> output00 = new ArrayList<CharFunc>();
		transitions.add(new SFTInputMove<CharPred, CharFunc, Character>(0, 0, ba.MkNot(new CharPred('<')), output00));

		List<CharFunc> output01 = new ArrayList<CharFunc>();
		transitions.add(new SFTInputMove<CharPred, CharFunc, Character>(0, 1, new CharPred('<'), output01));

		List<CharFunc> output11 = new ArrayList<CharFunc>();
		transitions.add(new SFTInputMove<CharPred, CharFunc, Character>(1, 1, new CharPred('<'), output11));

		List<CharFunc> output13 = new ArrayList<CharFunc>();
		output13.add(new CharConstant('<'));
		output13.add(CharOffset.IDENTITY);
		transitions.add(new SFTInputMove<CharPred, CharFunc, Character>(1, 3, ba.MkNot(new CharPred('<')), output13));

		List<CharFunc> output30 = new ArrayList<CharFunc>();
		output30.add(CharOffset.IDENTITY);
		transitions.add(new SFTInputMove<CharPred, CharFunc, Character>(3, 0, new CharPred('>'), output30));

		Map<Integer, Set<List<Character>>> finStatesAndTails = new HashMap<Integer, Set<List<Character>>>();
		finStatesAndTails.put(0, new HashSet<List<Character>>());
		finStatesAndTails.put(1, new HashSet<List<Character>>());

		return SFT.MkSFT(transitions, 0, finStatesAndTails, ba);
	}
	
	
	public void getTags() throws TimeoutException {
		GetTags = GetTag.MkGetTagsSFT();
		System.out.println(GetTags.toDotString(ba));
		System.out.println(GetTags.isDeterministic());
		
		SFA<CharPred, Character> domain = GetTags.getDomain(ba).removeEpsilonMoves(ba);
		assertTrue(domain.accepts(lOfS(""), ba));
		assertTrue(domain.accepts(lOfS(""), ba));
		
		SFA<CharPred, Character> range = GetTags.getOverapproxOutputSFA(ba).removeEpsilonMoves(ba);
		
		System.out.println(domain);
		System.out.println(range);
//		System.out.println(domain.toDotString(ba));
//		System.out.println(range.toDotString(ba));
		
		int[] fraction = new int[] {1, 1};
		
		List<Pair<String, String>> examples = new ArrayList<Pair<String, String>>();
		examples.add(new Pair<String, String>("<<s>", "<s>"));
		
		
		ConstraintsTestSymbolic.customConstraintsTest(domain, range, 3, 2, fraction, examples, null, false);
	}
	
	/* Deterministic variant of GetTags */
	@Test
	public void getTagsMod() throws TimeoutException {
		GetTags = MkGetTagsSFTMod();
		System.out.println(GetTags.toDotString(ba));
		System.out.println(GetTags.isDeterministic());
		
		SFA<CharPred, Character> domain = GetTags.getDomain(ba).removeEpsilonMoves(ba).determinize(ba);
		assertTrue(domain.accepts(lOfS(""), ba));
		assertTrue(domain.accepts(lOfS(""), ba));
		
		SFA<CharPred, Character> range = GetTags.getOverapproxOutputSFA(ba).removeEpsilonMoves(ba).determinize(ba);
		
		System.out.println(domain);
		System.out.println(range);
		System.out.println(domain.toDotString(ba));
		System.out.println(range.toDotString(ba));
		
		int[] fraction = new int[] {1, 1};
		
		List<Pair<String, String>> examples = new ArrayList<Pair<String, String>>();
		examples.add(new Pair<String, String>("<<s>", "<s>"));
		
		ConstraintsTestSymbolic.customConstraintsTest(domain, range, 3, 2, fraction, examples, null, false);
	}
	
	
	private static List<Character> lOfS(String s) {
		List<Character> l = new ArrayList<Character>();
		char[] ca = s.toCharArray();
		for (int i = 0; i < s.length(); i++)
			l.add(ca[i]);
		return l;
	}
}