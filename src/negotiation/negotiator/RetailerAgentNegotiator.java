package negotiation.negotiator;

import java.util.ArrayList;
import java.util.Map;

import model.Offer;
import negotiation.Issue;
import negotiation.Strategy;
import negotiation.Strategy.Item;

public class RetailerAgentNegotiator extends AgentNegotiator {

	public RetailerAgentNegotiator(double maxNegotiationTime,
			ArrayList<Strategy> strategies, Map<Item, Double> scoreWeights) {
		super(maxNegotiationTime, strategies, scoreWeights);
		// TODO Auto-generated constructor stub
	}

	@Override
	public double scoreFunction(double nextVal, double minVal, double maxVal) {
		// TODO Auto-generated method stub
		//simple increasing linear score function for supplier
		double val=0;
		double m=1/(maxVal-minVal);
		val=m*nextVal-m*(maxVal)+1;
		return val;
	}
	
	
}
