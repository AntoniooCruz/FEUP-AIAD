package Agents;

import AgentBehaviours.AirplaneLanded;
import AgentBehaviours.ListeningTowerBehaviour;
import AuxiliarClasses.AgentType;
import AuxiliarClasses.AirplaneInfo;
import gui.AirportGUI;
import jade.core.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.proto.SubscriptionInitiator;

import java.util.*;

public class ControlTower extends Agent{

    Comparator<AirplaneInfo> airplaneComparator = new Comparator<AirplaneInfo>() {
        @Override public int compare(AirplaneInfo a1, AirplaneInfo a2) {
            if(!a1.getLocalName().equals(a2.getLocalName())){
                if(a1.getTimeToTower() > a2.getTimeToTower())
                    return 1;
                else if(a1.getFuel() < 5 && a1.getFuel() < a2.getFuel())
                    return 1;
                else if (companyPriorities.get(a1.getLocalName().replaceAll("\\d","")) > companyPriorities.get(a2.getLocalName().replaceAll("\\d","")))
                    return 1;
                else if (a1.getPassengers() > a2.getPassengers())
                    return 1;
                else
                    return -1;
            }
            else
                return 0;
        }
    };

    private TreeSet<AirplaneInfo> airplanes = new TreeSet<>(airplaneComparator);
    private Map<String, Integer> companyPriorities = new HashMap<>();

    private Vector<AID> passenger_vehicles;

    private Character[][] map;
    private Character[][] vehicleMap;
    private int currLine;
    private AirportGUI gui;


    public ControlTower() {
        this.passenger_vehicles = new Vector<>();

        map = new Character[20][20];
        for (Character[] row: map)
            Arrays.fill(row, '*');

        vehicleMap = new Character[3][10];
        for (Character[] row: vehicleMap)
            Arrays.fill(row, '*');

        map[9][0] = 'C';
        currLine = 0;
    }

    // **** GETTERS AND SETTERS ****
    public Vector<AID> getPassenger_vehicles() {
        return passenger_vehicles;
    }

    public Character[][] getMap() {
        return map;
    }

    public Character[][] getVehicleMap() {
        return vehicleMap;
    }

    public void setGui(AirportGUI gui) {
        this.gui = gui;
    }

    public void takeDown() {
        System.out.println(getLocalName() + ": done working.");
    }

    public void setup() {
        System.out.println("New control tower");
        initialPassengerVehicleSearch();
        subscribePassengerVehicleAgent();
        addBehaviour(new ListeningTowerBehaviour(this));
    }

    private DFAgentDescription passengerVehicleDFAgentDescriptorCreator() {
        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(AgentType.PASSENGER_VEHICLE.toString());
        dfd.addServices(sd);
        return dfd;
    }

    private void initialPassengerVehicleSearch(){
        DFAgentDescription dfd = passengerVehicleDFAgentDescriptorCreator();

        try {
            DFAgentDescription[] search_result = DFService.search(this, dfd);

            for(DFAgentDescription vehicle : search_result)
                System.out.println(this.passenger_vehicles.add(vehicle.getName()));
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    public void initializePassengerGUI() {
        Iterator iterator = this.passenger_vehicles.iterator();
        int i = 0, j = 0;
        System.out.println(this.passenger_vehicles.size());
        while (iterator.hasNext()) {
            if(i > 4) {
                i = 0;
                j++;
            }

            this.vehicleMap[i][j] = 'A';
            i = i + 2;
        }

        gui.getVehiclePanel().repaint();
        gui.getVehiclePanel().setFocusable(true);
        gui.getVehiclePanel().requestFocusInWindow();
    }

    private void subscribePassengerVehicleAgent() {
        DFAgentDescription template = passengerVehicleDFAgentDescriptorCreator();
        
        addBehaviour( new SubscriptionInitiator( this,
                DFService.createSubscriptionMessage( this, getDefaultDF(),
                        template, null))
        {
            protected void handleInform(ACLMessage inform) {
                try {
                    DFAgentDescription[] dfds =
                            DFService.decodeNotification(inform.getContent());

                    ControlTower ct = (ControlTower) myAgent;
                    for(DFAgentDescription dfd : dfds) {
                        AID new_agent = dfd.getName();
                        if(!ct.getPassenger_vehicles().contains(new_agent)) {
                            ct.getPassenger_vehicles().add(new_agent);
                            System.out.println("New passenger vehicle on duty: " + new_agent.getLocalName());
                        }
                    }

                }
                catch (FIPAException fe) {fe.printStackTrace(); }
            }
        });
    }


    public void pushAirplane(AirplaneInfo airplane) {
        int y= -1;
        initializePassengerGUI();
        for (AirplaneInfo value : airplanes) {
            if (value.getLocalName().equals(airplane.getLocalName())) {
                y = value.getY();
                map[value.getX()][value.getY()] = '*';
                break;
            }
        }

        airplanes.removeIf(a1 -> a1.getLocalName().equals(airplane.getLocalName()));
        airplanes.add(airplane);

        Iterator<AirplaneInfo> iterator = airplanes.iterator();

        System.out.println();

        if(y != -1) {
            map[10 - airplane.getTimeToTower()][y] = 'A';
            airplane.setX(10 - airplane.getTimeToTower());
            airplane.setY(y);
        } else {
            map[10 - airplane.getTimeToTower()][currLine] = 'A';
            airplane.setX(10-airplane.getTimeToTower());
            airplane.setY(currLine);

            if(currLine < 19)
                currLine++;
            else currLine = 0;
        }

        gui.getPanel().repaint();
        gui.getPanel().setFocusable(true);
        gui.getPanel().requestFocusInWindow();

        gui.getVehiclePanel().repaint();
        gui.getVehiclePanel().setFocusable(true);
        gui.getVehiclePanel().requestFocusInWindow();


        // Loop over the TreeSet values
        // and print the values
        System.out.print("TreeSet: ");
        while (iterator.hasNext())
            System.out.print(iterator.next()
                    + ", ");
        System.out.println(); }

    public void landAirplane(AirplaneInfo airplane){
        addBehaviour(new AirplaneLanded(airplane,10 - companyPriorities.get(airplane.getLocalName().replaceAll("\\d",""))));

        for (AirplaneInfo value : airplanes) {
            if (value.getLocalName().equals(airplane.getLocalName())) {
                map[value.getX()][value.getY()] = '*';
                break;
            }
        }

        airplanes.removeIf(a1 -> a1.getLocalName().equals(airplane.getLocalName()) );
    }

    public void setPriority(String companyName, int priority) {
        companyPriorities.put(companyName,priority);
    }
}



