
package box;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ophélie EOUZAN
 * @author Mélanie DUBREUIL
 */
public class BoxAgent extends Agent {
    protected AID[] boxes; // Connaissances de l'agent
    protected int priorite; // Priorité de placement
    protected int x; // Position courante sur l'axe X
    protected int y; // Position courante sur l'axe Y 
    protected int yObjectif; // Position objectif sur l'axe Y : Comment modéliser l'objectif ? yObjectif ? ou bien AID de l'agent en dessous ? => yObjetif
    protected int xObjectif = -1; // Position objectif sur l'axe X: Undetermined until first box placed

    @Override
    protected void setup() {
        System.out.println("Hello! "+ this.getLocalName());
        
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            priorite = Integer.parseInt((String) args[0]);
            yObjectif = priorite;
            System.out.println("Box " + this.getLocalName() + " de priorité " + priorite);
            
            // Register the book-selling service in the yellow pages
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("box");
            sd.setName("JADE-box");
            dfd.addServices(sd);
            try {
                    DFService.register(this, dfd);
            }
            catch (FIPAException fe) {
                    fe.printStackTrace();
            }

            addBehaviour(new TickerBehaviour(this, 2000) {
                @Override
                protected void onTick() {
                    try {
                        // Update the list of seller agents
                        DFAgentDescription template = new DFAgentDescription();
                        ServiceDescription sd = new ServiceDescription();
                        sd.setType("box");
                        template.addServices(sd);
                        
                        //@TODO TickBehaviour to check every few time numer of boxes. When 4 found => start coordination
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result.length > 0) {
                            System.out.println("Found the following other boxes:");
                            AID[] knownBox = new AID[result.length];
                            for (int i = 0; i < result.length; ++i) {
                                if (!result[i].getName().getLocalName().equals(myAgent.getLocalName())) {
                                    knownBox[i] = result[i].getName();
                                    System.out.println(knownBox[i].getName());
                                }
                            }
                            if (knownBox.length == 2) {//TODO 4
                                // Perform the request
                                boxes = knownBox;
                                addBehaviour(new Coordination());
                                
                                //@Todo stop tickerBehaviour here
                            }
                        }
                    } catch (FIPAException ex) {
                        Logger.getLogger(BoxAgent.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        } else {
            // Make the agent terminate
            System.out.println("Pas de priorité spécifiée");
            doDelete();
        }
    }
    
    private boolean isActif()
    {
        return (this.x == this.xObjectif && this.y == this.yObjectif);
    }

    private class Coordination extends Behaviour {
        private int step = 0; // Etape courante de la coordination
        private AID prioritaire; // Agent prioritaire courant
        private int bestPriorite; // Meilleure priorité courante
        private MessageTemplate mt;
        private int repliesCnt = 0; // Nombre de réponses reçues

        @Override
        public void action() {
            switch (step) {
                case 0:
                    // Prioritizing

                    ACLMessage message = new ACLMessage(ACLMessage.INFORM); //ACLMessage.PROPAGATE ?
                    for (int i = 0; i < boxes.length; ++i) {
                        message.addReceiver(boxes[i]);
                    }

                    message.setContent(String.valueOf(priorite));
                    message.setConversationId("priorite-trade");
                    message.setReplyWith("priorite-"+System.currentTimeMillis()); // Unique value
                    myAgent.send(message);

                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("priorite-trade"), MessageTemplate.MatchInReplyTo(STATE_READY).MatchInReplyTo(message.getReplyWith()));

                    // Receive all proposals/refusals from seller agents
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.CONFIRM) {
                            // This is an offer 
                            int prioriteLocale = Integer.parseInt(reply.getContent());
                            if (prioritaire == null || prioriteLocale < bestPriorite) {
                                // This is the best offer at present
                                bestPriorite = prioriteLocale;
                                prioritaire = reply.getSender();
                                System.out.println("Updated priority of "+ myAgent.getLocalName() + " to " + bestPriorite);
                            }
                            
                            System.out.println("Received priority " + prioriteLocale + " from " + reply.getSender().getLocalName());
                            
                            repliesCnt++;
                            if (repliesCnt >= boxes.length) {
                                // We received all replies
                                step = 1;
                            }
                        }
                    }
                    break;
                case 2:
                    // Calculating plan = planning operations
                    if (xObjectif == x && yObjectif == y) {
                        step = 4;
                    }
                    
                    //@TODO PlanningBehavior instanceof OneShotBehaviour ?
                    
                    step = 3;
                    break;
                case 3:
                    // Executing plan
                    
                    //@TODO ExecutingBehavior instanceof OneShotBehaviour ?
                    
                    step = 4;
                    break;
                case 4:
                    // Blocking agent state
                    // Terminate agent ?
                    
                    
                    step = 5;
                    System.out.println("Fin de vie de "+myAgent.getLocalName());
                    break;
            }
        }

        @Override
        public boolean done() {
            return step == 5; // TODO fix final step
        }
    }
}
