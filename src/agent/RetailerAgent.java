package agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
//Used for the energy schedule
import java.util.Vector;

import annotations.Adjustable;
import jade.core.AID;
// Used to make the agent a ContractNetResponder Agent
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
// Used to make the agent a Service Provider Agent
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SSIteratedContractNetResponder;
import jade.proto.SSResponderDispatcher;
// Used to log exceptions
import jade.util.Logger;
import model.AgentDailyNegotiationThread;
import model.Demand;
import model.History;
import model.Offer;
import negotiation.Strategy;
import negotiation.Strategy.Item;
import negotiation.baserate.HomeBound;
import negotiation.baserate.RetailerBound;
import negotiation.negotiator.AgentNegotiator.OfferStatus;
import negotiation.negotiator.RetailerAgentNegotiator;
import negotiation.tactic.BehaviourDependentTactic;
import negotiation.tactic.ResourceDependentTactic;
import negotiation.tactic.Tactic;
import negotiation.tactic.TimeDependentTactic;
import negotiation.tactic.Tactic.Type;
import negotiation.tactic.behaviour.AverageTitForTat;
import negotiation.tactic.timeFunction.ResourceAgentsFunction;
import negotiation.tactic.timeFunction.ResourceEnergyStoreFunction;
import negotiation.tactic.timeFunction.ResourceTimeFunction;
import negotiation.tactic.timeFunction.TimeWeightedFunction;
import negotiation.tactic.timeFunction.TimeWeightedPolynomial;
import negotiation.baserate.BoundCalc;
import negotiation.baserate.RetailerBound;
import model.History;


public class RetailerAgent extends TradeAgent {
    
	private final boolean INC=true;// supplier mentality
	private RetailerAgentNegotiator negotiator;

	private class EnergyUnit {
		private int time;
		private int units;
		private int cost;
	}	
	
	private AgentDailyNegotiationThread dailyThread;
	
	//params needed to setup negotiators
	//coming from args
	@Adjustable private double maxNegotiationTime=10;
	@Adjustable private double ParamK=0.01;
	@Adjustable private double ParamBeta=0.5;
	@Adjustable private Tactic.Type tacticType;
	
	private double tacticTimeWeight=0.4;
	private double tacticResourceWeight=0.3;
	private double tacticBehaviourWeight=0.3;
	private int behaviourRange=2;
	
	private Vector<EnergyUnit> energyUnitSchedule = new Vector<EnergyUnit>();
	private Logger myLogger = Logger.getMyLogger(getClass().getName());
	
	
	
	// [install later] Amount of energy the retailer agent has, determines rate and threshold
	private Double energyStored = 100.00;
	
	protected void setup() {
		super.setup();
		//boundCalculator.calcBounds(AID id, int units, int time, History hist)
		//history.addTransaction(AID client, int units, double rate);
		
		// Sets the agent's properties (energy rate & threshold) to passed or default values

		Object[] args = this.getArguments();
		//set negotiation time from arguments
		if( args[0] instanceof Double)
			this.maxNegotiationTime=(Double)args[0];
		else
			this.maxNegotiationTime=Double.valueOf((String) args[0]);
		
		//retrieve K and Beta from args
		if( args[1] instanceof Double)
			this.ParamK=(Double)args[1];
		else
			this.ParamK=Double.valueOf((String) args[1]);
		
		if( args[2] instanceof Double)
			this.ParamBeta=(Double)args[2];
		else
			this.ParamBeta=Double.valueOf((String) args[2]);
		
		//retrieve tactic to use
		if( args[3] instanceof Tactic.Type)
			this.tacticType=(Tactic.Type)args[3];
		else
			this.tacticType=Tactic.Type.valueOf(((String) args[3]).toUpperCase());	
		
		if( args[4] instanceof Double)
			this.energyStored=(Double)args[4];
		else
			this.energyStored=Double.valueOf(((String) args[4]));	
		
		
		//Describes the agent as a retail agent
		setupServiceProviderComponent();

		dailyThread= new AgentDailyNegotiationThread();

		say("Retailer "+this.getName()+" with energy stored "+this.energyStored);


		
		// Template to filter messages as to only receive CFP messages for the CNR Behaviour
		MessageTemplate template = MessageTemplate.and(
				MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET),
				MessageTemplate.MatchPerformative(ACLMessage.CFP) );
		
		
		this.addBehaviour(new SSResponderDispatcher(this,template) {
			@Override
			protected Behaviour createResponder(ACLMessage initiationMsg) {
				// TODO Auto-generated method stub
				return new RetailerCNRBehaviour(myAgent, initiationMsg);
			}
		});
	}
	
	public void setupNegotiator()
	{
				
		//create TWfunction
		TimeWeightedFunction poly = new TimeWeightedPolynomial(this.ParamK, this.ParamBeta, this.maxNegotiationTime);
		//create RAFunction- for resource dep tactic- no lnger using
		ResourceTimeFunction rsrcFunc= new ResourceTimeFunction(this.ParamK, this.maxNegotiationTime);
		
		//create RAFunction- EnergyStore Func
		ResourceEnergyStoreFunction rsrcEnFunc= new ResourceEnergyStoreFunction(this.ParamK, this.energyStored);
		
		//create behTFT- for behaviour dep tactic
		AverageTitForTat tft = new AverageTitForTat(Item.PRICE);
		
		//create tactics
		Map<Tactic, Double> tactics = new HashMap<Tactic, Double>();
		
		if(tacticType.equals(Type.BEHAVIOURDEPENDENT))
		{
			BehaviourDependentTactic tactic3 = new BehaviourDependentTactic(tft, this.behaviourRange);
			tactics.put(tactic3, new Double(1));
		}
		else if(tacticType.equals(Type.RESOURCEDEPENDENT))
		{
			//time is the resource
			ResourceDependentTactic tactic2 = new ResourceDependentTactic(rsrcFunc, this.INC);
			tactics.put(tactic2, new Double(1));
		}
		else if(tacticType.equals(Type.TIMEDEPENDENT))
		{
			TimeDependentTactic tactic1 = new TimeDependentTactic(poly, this.INC);
			tactics.put(tactic1, new Double(1));
		}
		else
		{
			TimeDependentTactic tactic1 = new TimeDependentTactic(poly, this.INC);
			ResourceDependentTactic tactic2 = new ResourceDependentTactic(rsrcFunc, this.INC);
			BehaviourDependentTactic tactic3 = new BehaviourDependentTactic(tft, this.behaviourRange);
			tactics.put(tactic1, new Double(this.tacticTimeWeight));
			tactics.put(tactic2, new Double(this.tacticResourceWeight));
			tactics.put(tactic3, new Double(this.tacticBehaviourWeight));
		}
//		say("Tactic is "+tacticType);
		//create strategy and add tactics with weights
		Strategy priceStrat= new Strategy(Strategy.Item.PRICE);
		//changes as new tactics added
		
	
		try {
			priceStrat.setTactics(tactics);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("ERROR "+e.getMessage());
			
		}
		
		//add Strategy to negotiator's strategies
		ArrayList<Strategy> strats=new ArrayList<>();
		strats.add(priceStrat);
		
		//create score weights for negotiating items
		//ATM only price is considered so given full weight
		Map<Strategy.Item,Double>scoreWeights= new HashMap<>();
		//add only price item
		scoreWeights.put(Item.PRICE, new Double(1));
		
		//get my history object-simply creating new history, TODO object shud handle loading agent history, maybe pass in AID
	
		//create bound calc for price
		RetailerBound retailcalc= new RetailerBound(myHistory);
		
		
		//create negotiator with params
		this.negotiator= new RetailerAgentNegotiator( this.maxNegotiationTime, strats, scoreWeights,retailcalc);
	}
	
	private class RetailerCNRBehaviour extends SSIteratedContractNetResponder{
		private EnergyUnit currentUnitRequest = null;		

		
		RetailerCNRBehaviour(Agent a, ACLMessage initialMessage) {
			super(a, initialMessage);
			setupNegotiator();
			//get demand from initial Message
			Offer off = new Offer(initialMessage);
			Demand demand=off.getDemand();
			say("recieved demand "+demand.getContent() + " from " + initialMessage.getSender().getLocalName());
			//add negotiator to daily thread
			dailyThread.addHourThread(demand.getTime(), initialMessage.getSender(), negotiator.getNegotiationThread());
			//setup initial issue 
			negotiator.setInitialIssue(off);
			say("intial issue for "+initialMessage.getSender().getLocalName()+" issue "+negotiator.getItemIssue().get(Item.PRICE));

		}
		
		@Override
		protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
			
			//check if we have energy remaining if not dnt negotiate- maybe remove from df not selling anymore
			if(energyStored<=0)
			{
				say("No energy to sell good bye nigga");
				throw new RefuseException("No energy to sell");
			}
				
			//get offer received
			Offer offer= new Offer(cfp);
			//interprete offer
			OfferStatus stat=negotiator.interpretOffer(offer);
			if(stat.equals(OfferStatus.REJECT))
			{
				//update history
				addToHistory(negotiator, cfp, false, cfp.getSender());
				//reject proposal by throwing a refuse
				throw new RefuseException("Times up ");
			}
			ACLMessage reply = cfp.createReply();
			reply.setPerformative(ACLMessage.PROPOSE);
			if(stat.equals(OfferStatus.ACCEPT))
			{
				//responder cant accept cfps so sending same offer received from cfp as proposal	
				reply.setContent(cfp.getContent());
				//add it as counter offer to negotiation thread
				//this is due to protocol limitations, otherwise handled correctly
				addToHistory(negotiator, cfp, true, cfp.getSender());
				negotiator.getNegotiationThread().addOffer(offer);
			}			
			if(stat.equals(OfferStatus.COUNTER))
			{
				
				//send reply with counter offer						
				reply.setContent(negotiator.getLastOffer().getContent());
				say("counter offer "+reply.getContent());
			}				
			return reply;
			
		}
		
		@Override
		protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
			say("yay offer accepted");
			ACLMessage resp=accept.createReply();
			say("Accepted"+propose.getContent());
			Offer off= new Offer(propose);
			//reduce energy stored- i.e. delivering energy to home
			int deliverUnits=off.getDemand().getUnits();
			RetailerAgent.this.energyStored-=deliverUnits;
			resp.setContent("Accepted "+propose.getContent()+" and Units delivered "+deliverUnits);
			say("Energy Remaining "+RetailerAgent.this.energyStored);
			resp.setPerformative(ACLMessage.INFORM);
			return resp;
			
			
		}

		protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
			
			say("Proposal rejected "+reject.getContent());
			//add to history
			addToHistory(negotiator, propose, false, reject.getSender());
		}
		
		@Override
		public int onEnd() {
			// TODO Auto-generated method stub
//			 System.out.println(dailyThread.toString());
			 myHistory.saveTransactionHistory();
			return super.onEnd();
		}
		
		
	}

	
	
	// Temporary method until the method of setting rate is determined

		
	private void setupServiceProviderComponent () {
		DFAgentDescription dfd = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription(); 
		
		// Maybe also add another service description of electricity buyer for when we implement agents buying power
		
		// Create service description
		sd.setType("RetailerAgent"); 
		sd.setName(getName());
		sd.setOwnership("TradeNetwork");
		
		// Add service to agent description
		dfd.setName(getAID());
		dfd.addServices(sd);
		
		// Try to register the agent with its description
		try {
			DFService.register(this,dfd);	
		} catch (FIPAException e) {
			myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot register with DF", e);
			doDelete();
		}
	}
	
	
	private int convStrToInt (String str) {
		int value = 0;
		
		try {
			value = Integer.parseInt(str);	
		} catch (NumberFormatException e) {
			myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot convert \"" + str  + "\" into an integer", e);
			doDelete();
		}
		
		return value;
	}

}
