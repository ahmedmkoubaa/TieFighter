package tiefighter;

import agents.LARVAFirstAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import swing.LARVACompactDash;
import swing.LARVADash;

public class Practica2Alt extends LARVAFirstAgent{

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM,
        COMISSIONING, JOINSESSION, SOLVEPROBLEM,
        CLOSEPROBLEM, EXIT
    }


    Status mystatus;

    // MUNDOS
    // P1: Dagobah
    // P2-basicos: Abafar Batuu Chandrila Dathomir Endor
    // P2-avanzados: Felucia Hoth Mandalore Tatooine

    String service = "PManager", problem = "Hoth",
            problemManager = "", content, sessionKey,
            sessionManager, storeManager, sensorKeys;

    // Resolver problema de manera sencilla usando
    // un vector de acciones fijas, util solo para esta practica
    String actions [] = {
        "LEFT", "LEFT", "LEFT",
        "MOVE", "MOVE", "MOVE",
        "MOVE", "MOVE", "MOVE",
        "MOVE", "MOVE", "MOVE",
        "MOVE", "CAPTURE"
    };
    private int sigAction = 0;
    private final int gradoTotal = 360;

    private double maxEnergy = -1;
    private final double porcentajeLimite = 0.4;
    private final double porcentajeCercania = 0.8;
    private final int alturaCercania = 20;

    // Atributos en los que se almacenaran los valores
    // correspondientes a umbrales de recarga
    private double umbralLimiteRecarga;
    private double umbralCercaniaRecarga;

    // indica que estamos evitando lo que se encuentre a nuestra izquierda
//    private Boolean evitandoIzquierda = false;

    // indica que se esta evitando lo que se encuentra a nuestra derecha
//    private Boolean evitandoDerecha = true;
    // para indicar que el tiefighter esta esquivando ciertos obstaculos
    private boolean evitando = false;

    private ArrayList<double[]> casillasRecorridasSinExito = new ArrayList<>();

    private ArrayList<String> acciones = new ArrayList<>();

    int width, height, maxFlight;

    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
                "ALIVE",
                "ONTARGET",   // No
                "GPS",        // No
                "COMPASS",
                "LIDAR",
                "ALTITUDE",   // No
                "VISUAL",     // No
                "ENERGY",
                "PAYLOAD",
                "DISTANCE",
                "ANGULAR",
                "THERMAL"     // No
            };
    boolean step = true;

    @Override
    public void setup() {
        super.setup();
        logger.onOverwrite();
        logger.setLoggerFileName("mylog.json");
        this.enableDeepLARVAMonitoring();
        Info("Setup and configure agent");
        mystatus = Status.CHECKIN;
        exit = false;

//        this.myDashboard = new LARVADash(LARVADash.Layout.DASHBOARD, this);
        this.myDashboard = new LARVADash(this);
        doActivateLARVADash();
    }

    @Override
    public void Execute() {
        Info("Status: " + mystatus.name());
        if (step) {
            step = this.Confirm("The next status will be " + mystatus.name()
                                + "\n\nWould you like to continue step by step?");
        }
        switch (mystatus) {
            case CHECKIN:
                mystatus = MyCheckin();
                break;
            case OPENPROBLEM:
                mystatus = MyOpenProblem();
                break;
            case COMISSIONING:
                mystatus = MyComissioning();
                break;
            case JOINSESSION:
                mystatus = MyJoinSession();
                break;
            case SOLVEPROBLEM:
                mystatus = MySolveProblem();
                break;
            case CLOSEPROBLEM:
                mystatus = MyCloseProblem();
                break;
            case CHECKOUT:
                mystatus = MyCheckout();
                break;
            case EXIT:
            default:
                exit = true;
                break;
        }
    }

    @Override
    public void takeDown() {
        Info("Taking down and deleting agent");
        this.saveSequenceDiagram("./" + this.problem + ".seqd");
        super.takeDown();
    }

    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        if (!loadMyPassport("MyPassport.passport")) {
            Error("Unable to load passport file");
            return Status.EXIT;
        }
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.OPENPROBLEM;
    }

    public Status MyOpenProblem() {
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        this.outbox = new ACLMessage();

        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem);

        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);

        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");

        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return Status.COMISSIONING;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }


    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem Helloworld, session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }

    public Status MyComissioning(){
        String localService = "STORE " + sessionKey;

        if (this.DFGetAllProvidersOf(localService).isEmpty()) {
            Error("Service STORE is down");
            return Status.CLOSEPROBLEM;
        }

        storeManager = this.DFGetAllProvidersOf(localService).get(0);
        Info("Found store manager " + storeManager);

        sensorKeys = "";
        for (String s: mySensors) {
            outbox = new ACLMessage();
            outbox.setSender(this.getAID());
            outbox.addReceiver(new AID (storeManager, AID.ISLOCALNAME));
            outbox.setContent("Request product " + s + " session " + sessionKey);

            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();

            if (inbox.getContent().startsWith("Confirm")) {
                Info("bought sensor " + s) ;
                sensorKeys += inbox.getContent().split(" ")[2] + " ";
            } else {
                this.Alert("Sensor " + s + " could not be obtained");
                return Status.CLOSEPROBLEM;
            }
        }

        Info("bought all sensor keys " + sensorKeys);
//        return Status.CLOSEPROBLEM;
        return Status.JOINSESSION;
    }

    public Status MyJoinSession(){
        session = session.createReply();
        session.setContent("Request join session " + sessionKey
                         + " attach sensors " + sensorKeys);

        this.LARVAsend(session);
        session = this.LARVAblockingReceive();

        String parse[] = session.getContent().split(" ");

        if (parse[0].equals("Confirm")) {
            width = Integer.parseInt(parse[8]);
            height = Integer.parseInt(parse[10]);
            maxFlight = Integer.parseInt(parse[14]);
            return Status.SOLVEPROBLEM;
        } else {
            Alert("Error: joining session: " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
    }


    // Metodo que procesa los sensores leidos y toma decisiones para llegar
    // a capturar al objetivo del mundo que se resuelve
    private String myTakeDecision() {
        String nextAction = "";
        final double miCasilla[] = this.myDashboard.getGPS();
        final double miAltura = miCasilla[2];

        Info("\n\n\n\n\n\nEsta es mi casilla --> "
            + miCasilla[0] + ":" + miCasilla[1]
        +  "\n\n\n\n");


        if (maxEnergy < 0) {
            maxEnergy = myDashboard.getEnergy() + myDashboard.getEnergyBurnt();
            umbralLimiteRecarga = porcentajeLimite * maxEnergy;
            umbralCercaniaRecarga = porcentajeCercania * maxEnergy;
            nextAction = "RECHARGE";
        }
        else {
            // Si el dron sigue vivo y tiene energia
            if (myDashboard.getAlive() && myDashboard.getEnergy() > 0) {
                int lidar[][] = this.myDashboard.getLidar();

                // Vamos a recargar cuando estemos a cierto porcentaje de bateria
                // y si tenemos buena bateria pero estamos cerca del suelo
                // aprovechamos para recargar
                if (myDashboard.getEnergy() <= umbralLimiteRecarga ||
                        (myDashboard.getEnergy() <= umbralCercaniaRecarga && myDashboard.getAltitude() <= alturaCercania)) {

                    // Recargar
                    if (lidar[5][5] > 0) {
                        nextAction = "DOWN";
                    } else {
                        nextAction = "RECHARGE";
                    }

                } else {
                    // Si no estamos sobre el objetivo
                    if (this.myDashboard.getDistance() > 0) {

                        final int compass = this.myDashboard.getCompass();
                        final double angular = this.myDashboard.getAngular();
                        
                        // Si no estamos apuntando hacia el objetivo, vamos a
                        // orientarnos hacia alla
                        if( !apuntandoHaciaObjetivo(angular, compass)) {

                            // Si no estamos evitando ningun obstaculo, vamos a
                            // a apuntar hacia el objetivo
                            if (!evitando) {
                                nextAction = getGiroMinimo(angular, compass);

                            } else {

                                // Si estamos evitando algun obstaculo, vamos a obtener altura y coordenadas
                                // de casilla en direccion al objetivo (la direccion que nos interesa para llegar
                                // lo antes posible al objetivo
                                double alturaDireccionAngular = mapearAlturaSegunAngulo((int)angular, lidar);
                                double casilladireccionAngular[] = getCasillaDireccionAngulo(angular, miCasilla);

                                // Si dicha casilla es accesible, es decir, no
                                // se ha recorrido aun, entonces puede ser viable
                                // y además i no hay que subir mas o hay que subir
                                // pero podemos subir mas porque lo permite el maxFlight
                                if (esAccesible(casilladireccionAngular)
                                        && (alturaDireccionAngular >= 0
                                        || (alturaDireccionAngular < 0 && miAltura < maxFlight))) {

                                    // Entonces nos orientamos para ir hacia alla,
                                    // dejamos por lo tanto de evitar obstaculos
                                    nextAction = getGiroMinimo(angular, compass);
                                    evitando = false;

                                    Info("\n\n\n\n\n --- 1) SIGUIENTE ACCION SERA GIRAR DEJAMOS DE EVITAR\n\n");

                                } else {

                                    // En caso de que la casilla en direccion objetivo
                                    // fuese recorrida sin exito previamente o bien
                                    // se encuente en una posicion demasiado alta,
                                    // entonces marcamos la casilla en la que estamos
                                    // como recorrida, ya que pasar por aqui
                                    // no nos permite llegar al objetivo

//                                    aniadirCasillaRecorrida(miCasilla);

                                    // obtenemos altura y coordenadas de casilla
                                    // que indica el compass, es decir, hacia donde
                                    // apunta el tieFighter actualmente
                                    double alturaDireccionCompass = mapearAlturaSegunAngulo(compass, lidar);
                                    double casillaDireccionCompass[] = getCasillaDireccionAngulo(compass, miCasilla);


                                    Info("Siguiente casilla según compass es: " +
                                            casillaDireccionCompass[0]
                                            + ":" +
                                            casillaDireccionCompass[1]);

                                    // Si la siguiente casilla es accesible y
                                    // además, la altura es inferior o bien es
                                    // superior pero puedo subir mas porque
                                    // me lo permite el maxFlight
                                    if (esAccesible(casillaDireccionCompass)
                                            && (alturaDireccionCompass >= 0
                                            || (alturaDireccionCompass < 0 && miAltura < maxFlight))) {

//                                        if (esAccesible(miCasilla)) casillasRecorridasSinExito.add(miCasilla);
                                        aniadirCasillaRecorrida(miCasilla);
                                        nextAction = "MOVE";

                                        Info("\n\n\n\nSIGUIENTE ACCION SERA MOVE, PERO SEGUIMOS EVITANDO\n\n");
                                        Info("Diferencia entre compass y angular: " + getDistanciaEntreAngulos(angular, compass));
//                                        evitando = false;
//                                        if (getDistanciaEntreAngulos(compass, angular) == gradoTotal/2) evitando = false;
                                    } else {
                                        // Como la casilla de enfrente no nos interesa
                                        // o no podemos acceder a ella, entonces
                                        // giramos nuevamente para eviat dicha casilla
                                        nextAction = "LEFT";
                                    }
                                }
                            }
                        } else {
                            // Este es el caso basico en el que estamos apuntando al objetivo y solo
                            // tenemos que desplazarnos en linea recta

                            double alturaDireccionCompass = mapearAlturaSegunAngulo(compass, lidar);
                            double casillaDireccionCompass[] = getCasillaDireccionAngulo(compass, miCasilla);

                            Info("\n\nCasilla direccion compass es " +
                                    casillaDireccionCompass[0]
                                    + ":" +
                                    casillaDireccionCompass[1]);

                            if (esAccesible(casillaDireccionCompass)) {
                                if (alturaDireccionCompass < 0) {
                                    if (miAltura == maxFlight) {
                                        // No podemos avanzar, debemos evitar
                                        Info("\n\n\n\n\n Mi casilla recorrida que voy a añadir es: " + miCasilla[0] + ":" + miCasilla[1] + "\n\n\n");
                                        aniadirCasillaRecorrida(miCasilla);

                                        Alert("He aniadido mi casilla recorrida " + miCasilla[0] + ":" + miCasilla[1]);
                                        evitando = true;
                                        nextAction = "LEFT";
                                    } else {
                                        // Debemos subir para avanzar
                                        nextAction = "UP";
                                    }
                                } else {
                                    // Si la casilla de enfrente es inferior a mi
                                    nextAction = "MOVE";
                                }
                            } else {
                                aniadirCasillaRecorrida(miCasilla);
//                                if (esAccesible(miCasilla))
//                                    casillasRecorridasSinExito.add(miCasilla);
                                evitando = true;
                                nextAction = "LEFT";
                            }
                        }
                    } else {
                        // Si estamos sobre el objetivo pero mas altos que
                        // este, habra que descender
                        if (lidar[5][5] > 0) {
                            nextAction = "DOWN";
                        } else {
                            // capturar objetivo
                            nextAction = "CAPTURE";
                        }
                    }
                }
            } else {
                Alert("TieFighter sin vida, fin del juego");
            }
        }

//        if (nextAction == "MOVE") {
//            double casilla[] = getCasillaDireccionAngulo(myDashboard.getCompass(), myDashboard.getGPS());
//
//            for (double c[]: casillasRecorridasSinExito) {
//                if (c[0] == casilla[0] && c[1] == casilla[1]) {
//                    nextAction = "LEFT";
//                    evitando = true;
//
//                    Info("VAS A HACER MOVE A UNA CASILLA PROHIBIDA, IDIOTA");
//                    break;
//                }
//            }
//        }


        String casillasInfo = "\n\n\nCASILLAS PROHIBIDAS:\n";
        for (double c[]: casillasRecorridasSinExito){
            casillasInfo += c[0] + ":" + c[1] + "\n";
        }

        casillasInfo += "\n\n\n";

        Info(casillasInfo);
        return nextAction;
    }

    // Aniade la casilla pasada al vector de casillas recorridas sin exito
    // si no fue aniadida correctamente en alguna fase anterior
    private void aniadirCasillaRecorrida(final double [] miCasilla) {
        if (esAccesible(miCasilla)) casillasRecorridasSinExito.add(miCasilla);
    }

    // Devuelve la ditancia entre dos angulos sobre el grado total definido
    private double getDistanciaEntreAngulos(final double d1, final double d2) {
     return ((d1 - d2 + gradoTotal) % gradoTotal);
    }

    // Devuelve true si la diferencia entre angular y
    // compass es un giro o menos
    private boolean apuntandoHaciaObjetivo(final double angular, final double compass) {
        double distanciaAngulo = getDistanciaEntreAngulos(angular, compass);
        return (distanciaAngulo < 45);
    }


    // Devuelve la accion de giro que lleve el menor numero de
    // acciones para que el compass apunte hacia donde el angular
    private String getGiroMinimo(final double angular, final double compass) {
        double distanciaAngulo = getDistanciaEntreAngulos(angular, compass);

        // Elegir distancia de giro minimo
        if ( distanciaAngulo < gradoTotal/2 ) {
            return "LEFT";
        }
        else {
            return "RIGHT";
        }
    }

    // Metodo que devuelve si una casilla es accesible o no
    // Busca la casilla pasada en la lista de prohibidas, si la encuentra
    // devuelve false, si no la encuentra, entonces true.
    private boolean esAccesible(final double casilla[]) {
        boolean accesible = true;
        for (double c[]: casillasRecorridasSinExito) {
            if (c[0] == casilla[0] && c[1] == casilla[1]) {
                accesible = false;
                break;
            }
        }

        // False si la encuentra, true en otro caso
        return accesible;
    };

    // Metodo privado que devuelve la altura de la casilla que se encuentre
    // en la direccion que apunte el compass (sobre el lidar pasado)
    private int mapearAlturaSegunAngulo (final int angulo, final int lidar [][]) {
        // Hallar altura casilla de enfrente
        int alturaBuscada = -1;

        // Mapea por rangos, mas ineficiente que Switch
        // pero mas flexible y adaptativo
        if (angulo >= 0 && angulo < 45) {
            alturaBuscada = lidar[5][6];
        } else if (angulo >= 45 && angulo < 90) {
            alturaBuscada = lidar[4][6];
        } else if (angulo >= 90 && angulo < 135) {
            alturaBuscada = lidar[4][5];
        } else if (angulo >= 135 && angulo < 180) {
            alturaBuscada = lidar[4][4];
        } else if (angulo >= 180 && angulo < 225) {
            alturaBuscada = lidar[5][4];
        } else if (angulo >= 225 && angulo < 270) {
            alturaBuscada = lidar[6][4];
        } else if (angulo >= 270 && angulo < 315) {
            alturaBuscada = lidar[6][5];
        } else if (angulo >= 315 && angulo < 360) {
            alturaBuscada = lidar[6][6];
        } else {
            Alert("Angulo no reconocido " + angulo);
            alturaBuscada = -1;
        }

        return alturaBuscada;
    }

    // Metodo que devuelve las coordenadas de la casilla que se este apuntando
    // con el angulo desde la casilla actual indicada por gps[]
    private double [] getCasillaDireccionAngulo(double angulo, final double gps[]) {
        double casillaCalculada [] = gps;

         if (angulo >= 0 && angulo < 45) {
            casillaCalculada[0]++;

        } else if (angulo >= 45 && angulo < 90) {
            casillaCalculada[1]--;
            casillaCalculada[0]++;

        } else if (angulo >= 90 && angulo < 135) {
            casillaCalculada[1]--;

        } else if (angulo >= 135 && angulo < 180) {
            casillaCalculada[1]--;
            casillaCalculada[0]--;

        } else if (angulo >= 180 && angulo < 225) {
            casillaCalculada[0]--;

        } else if (angulo >= 225 && angulo < 270) {
            casillaCalculada[1]++;
            casillaCalculada[0]--;

        } else if (angulo >= 270 && angulo < 315) {
            casillaCalculada[1]++;

        } else if (angulo >= 315 && angulo < 360) {
            casillaCalculada[1]++;
            casillaCalculada[0]++;

        } else {
            Alert("Angulo no reconocido " + angulo);
            casillaCalculada[0] = casillaCalculada[1] = -1;
        }

        return casillaCalculada;
    }


    // Metodo importante, resuelve el problema que se abra, en este caso
    // el de Dagobah
    public Status MySolveProblem() {

        // ------------------------------------------------------------------ //
        // Obtener informacion de sensores desde LARVA
        boolean lecturaCorrecta = myReadSensors();
        boolean ejecucionCorrecta = false;

        // ------------------------------------------------------------------ //
        // Tomar una decision
        if (lecturaCorrecta) {
            String nextAction = myTakeDecision();

            // -------------------------------------------------------------- //
            // Realizar la ejecucion de la accion
            ejecucionCorrecta = myExecuteAction(nextAction);
        }


        // ------------------------------------------------------------------ //
        // Actualizar estado de la simulacion

        // Si algo fallo se cierra el problema, en otro caso se sigue
        if (!ejecucionCorrecta || !lecturaCorrecta) {
            return Status.CLOSEPROBLEM;
        } else {

            // Si tenemos algun objetivo capturado, cerramos el problema
            if (myDashboard.getPayload()> 0) {
                Info("Objetivo capturado correctamente, cerrando problema");
                return Status.CLOSEPROBLEM;
            }
            else {
                // En otro caso seguimos resolviendolo
                return Status.SOLVEPROBLEM;
            }
        }
    }

    // lee sensores mediante peticiones al sensorManager, si fue lectura
    // correcta devuelve true, en otro caso devuelve false
    private boolean myReadSensors() {
        this.outbox = new ACLMessage();

        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setContent("Query sensors session " + sessionKey);

        this.LARVAsend(outbox);
        Info("Request query sensors session to " + sessionManager);

        inbox = LARVAblockingReceive();
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();

        // Comprobar que la lectura fuese correcta
        if (content.startsWith("Refuse") || content.startsWith("Failure")) {
            Alert(content);
            return false;
        }

        Info("Mi casilla actual es: " + myDashboard.getGPS()[0] + ":" + myDashboard.getGPS()[1]);
        return true;
    }

    // Ejecuta una accion enviando un mensaje al session manager
    // Devuelve true en caso de que la accion se ejecutase correctamente
    // false en otro caso diferente
    private boolean myExecuteAction(String accion){
        this.outbox = new ACLMessage();

        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setContent("Request execute " + accion + " session " + sessionKey);

        this.LARVAsend(outbox);
        Info("Request executing action " + accion + " to " + sessionManager);

        inbox = LARVAblockingReceive();
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();

        if (content.startsWith("Inform")) {
            return true;
        } else {
            Alert(content);
            return false;
        }
    }
}