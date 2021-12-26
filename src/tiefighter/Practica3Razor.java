package tiefighter;

import agents.LARVAFirstAgent;
import geometry.Point;
import geometry.Vector;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
//import swing.LARVACompactDash;
import swing.LARVADash;
import swing.LARVAMiniDash;


/*
* @author Jaime
*/

public class Practica3Razor extends LARVAFirstAgent{  //Practica3Razor


    enum Status {
        CHECKIN, CHECKOUT, 
        COMISSIONING, JOINSESSION, 
        SOLVEPROBLEM, EXIT, WAIT
    }
    
    
    Status mystatus;
    
    String service = "PManager", problem = "Tatooine",
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
   
    private final double porcentajeLimite = 0.10;
    private final double porcentajeCercania = 0.8;
    private final int alturaCercania = 20;
    
    // Atributos en los que se almacenaran los valores
    // correspondientes a umbrales de recarga
    private double maxEnergy = 3500;
    private double umbralLimiteRecarga = porcentajeLimite * maxEnergy;
    private double umbralCercaniaRecarga; 
    
    // indica que estamos evitando lo que se encuentre a nuestra izquierda
    private Boolean evitandoIzquierda = false;
    
    // indica que se esta evitando lo que se encuentra a nuestra derecha
    private Boolean evitandoDerecha = true;
    
    private ArrayList<double[]> casillasProhibidas = new ArrayList<>();
    
    private ArrayList<String> acciones = new ArrayList<>();
    
    /*
    * @author Jaime
    */
    private String password = "106-WING-14";
    
    private int initX;
    private int initY;
    
    private int objetivoX;
    private int objetivoY;
    private int objetivoZ;
    private int myAngular;
    
    /*
    * @author Ahmed
    */
    // Flag para detectar primera ejecucion
    private boolean despegando = true;
    
    private ArrayList<String> jedisEncontrados = new ArrayList<>();
    private ArrayList<Point> puntos = new ArrayList<>();
    
    private int compass = 0;
    
    /*
    *@author Ahmed
    */
    // Emular sensor de energy, se actualiza
    // en myReadSensors y en myExecuteAction
    private double myEnergy = maxEnergy;
    private final int costeAccion = 1;
    private final int costeSensor = 1;
    
    // Acciones cuya ejecucion se cobra a costeAccion
    private String [] accionesCobrables =
            new String[] {
                "UP", 
                "DOWN", 
                "LEFT", 
                "RIGHT", 
                "MOVE"
            } ;
    
    /*
    * @author Ahmed
    */
    // Emular GPS
    private double [] myGPS;
    
    private String map;
    
    private double alturaTie;
    private boolean recargando = false;
    private boolean recargaPedida = false;
    
    /*
    * @author Ahmed
    */
    private ArrayList<String> casillasSuscritas = new ArrayList<>();
    
    int width, height, maxFlight;
    
    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
//                "ALIVE",
//                "ONTARGET",   // No 
//                "GPS",        // si
//                "COMPASS",  // SI
//                "LIDAR",  // SI
//                "ALTITUDE",   // No
//                "VISUAL",     // No
//                "ENERGY",       // SI
//                "PAYLOAD",
//                "DISTANCE",
//                "ANGULAR",    // SI
//                "THERMALHQ"     // SI
            };
    
    
    boolean step = false; // TEMPORAL
    
    /*
    * @author Jaime
    */
    @Override
    public void setup() {
        super.setup();
        logger.onOverwrite();
        logger.setLoggerFileName("mylog.json");

//        logger.offEcho();
//        this.enableDeepLARVAMonitoring();

        //this.enableDeepLARVAMonitoring();
        Info("Setup and configure agent");
        mystatus = Status.CHECKIN;
        exit = false;
        
        //this.myDashboard = new LARVADash(LARVADash.Layout.DASHBOARD, this);
        this.myDashboard = new LARVADash(this);
        //doActivateLARVADash();

    }

    /*
    * @author Jaime
    */
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
            case WAIT:
                mystatus = MyWait();
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

    /*
    * @author Jaime
    */
    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        if (!loadMyPassport("passport/MyPassport.passport")) {
            Error("Unable to load passport file");
            return Status.EXIT;
        }
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        DFSetMyServices(new String[]{"RAZOR " + password});
        return Status.WAIT;
    }
    
    /*
    * @author Jaime
    */
    public Status MyWait() {
        open = this.LARVAblockingReceive();
        sessionKey = open.getConversationId();
        
        
        outbox = open.createReply();
        outbox.setPerformative(ACLMessage.AGREE);
        outbox.setConversationId(sessionKey);
        outbox.setInReplyTo("Recruit crew for session " + password);
        outbox.setContent("");
        this.LARVAsend(outbox);
        
        // Esperar porque larva da fallos para procesar el mapa
        myDashboard.getMapLevel(1, 1);
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException ex) {
            Logger.getLogger(Practica3TieFighter.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        open = this.LARVAblockingReceive();
        initX = Integer.parseInt(open.getContent().split(" ")[0]);
        initY = Integer.parseInt(open.getContent().split(" ")[1]);
        int initZ = myDashboard.getMapLevel(initX, initY);
        
        // Para esta practica es siempre igual para otras no
        // es que larva falla y nodeja usar el mapa procesado
        maxFlight = myDashboard.getMaxlevel();
        maxFlight = 255;
        Info("Este es el max level: " + maxFlight);
        
        int cont = 0;
        int max = 150;
        
        while (initZ < 0 || initZ > maxFlight && (cont <= max)) {
            initZ = myDashboard.getMapLevel(initX, initY);
            Info("Esperando: " + initZ);
            cont++;
        }
        
        if (cont > max) {
            Error("ERROR AL OBTENER EL MAPA");
            return Status.CHECKOUT;
        }
        
        myAngular = 0;
        Info("X: " + initX + ", Y: " + initY);
        
        // Inicializar el GPS
        myGPS = new double [] {
            initX,                                  // X de Spawn
            initY,                                  // Y de Spawn
            initZ   // Z calculada de Spawn
        };
        
        Info("MYGPS: " + myGPS[0] + ", " + myGPS[1] + ", " + myGPS[2]);
        
        return Status.COMISSIONING;
    }

    /*
    * @author Jaime
    */
    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem Helloworld, session " + sessionKey);
        this.LARVAsend(outbox);
        open = this.LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        return Status.CHECKOUT;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }
    
    /*
    * @author Jaime
    */
    public Status MyComissioning(){
        String localService = "STORE " + sessionKey;
        
        if (this.DFGetAllProvidersOf(localService).isEmpty()) {
            Error("Service STORE is down");
            return Status.CHECKOUT;
        }
        
        storeManager = this.DFGetAllProvidersOf(localService).get(0);
        Info("Found store manager " + storeManager);
        
        sensorKeys = "";
        for (String s: mySensors) {
            outbox = new ACLMessage();
            outbox.setSender(this.getAID());
            outbox.addReceiver(new AID (storeManager, AID.ISLOCALNAME));
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setContent("Request product " + s + " session " + sessionKey);
            
            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();
            Info("Sensor: " + inbox.getContent());
            
            if (inbox.getContent().startsWith("Confirm")) {
                Info("bought sensor " + s) ;
                sensorKeys += inbox.getContent().split(" ")[2] + " ";
            } else {
                this.Alert("Sensor " + s + " could not be obtained");
                return Status.CHECKOUT;
            }
        }
        
        Info("bought all sensor keys " + sensorKeys);
        return Status.JOINSESSION;
    }

    /*
    * @author Jaime
    */    
    public Status MyJoinSession(){
        String localService = "SESSION MANAGER " + sessionKey;
        
        if (this.DFGetAllProvidersOf(localService).isEmpty()) {
            Error("Service SESSION is down");
            return Status.CHECKOUT;
        }
        
        sessionManager = this.DFGetAllProvidersOf(localService).get(0);
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setContent("Request join session " + sessionKey 
                         + " as " + "Razor" + " at " + initX + " " + initY +
                         " attach sensors " + sensorKeys);
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        String parse[] = session.getContent().split(" ");
        
        if (parse[0].equals("Confirm")) {
            outbox = open.createReply();
            outbox.setPerformative(ACLMessage.INFORM);
            outbox.setConversationId(sessionKey);
            outbox.setInReplyTo("TAKEOFF " + password);
            outbox.setContent(initX + " " + initY);
            this.LARVAsend(outbox);
            return Status.SOLVEPROBLEM;
        } else {
            Alert("Error: joining session: " + session.getContent());
            return Status.CHECKOUT;
        }
    }
    
    /*
    * @author Ahmed
    */
    // Metodo llamado cuando se detecta un jedi en X, Y
    // Comprueba si fue detectado previamente y  en caso 
    // de que no no, lo notifica al destroyer
    private void notificarJediEncontrado(double jediXReal, double jediYReal) {
        String pos = jediXReal + " " + jediYReal;

        Info("Esta es la posicion encontrada: " + pos);

        boolean aniadido = false;
        for (String s : jedisEncontrados) {
            if (s.equals(pos)) {
                aniadido = true;
            }
        }
        
//        aniadido = jedisEncontrados.contains(pos);
        
        if (!aniadido) {
            jedisEncontrados.add(pos);
                    
            /*
            @author Antonio
            @author Ahmed
                Enviamos mensaje al destroyer con los jedi encontrados
            */

            //Cambiamos la forma en la que se inserta la pos del jedi,
            //para utilizar split() y así obtenemos la x e y.
            outbox = open.createReply();
            outbox.setPerformative(ACLMessage.INFORM_REF);
            outbox.setConversationId(sessionKey);
            outbox.setOntology("COMMITMENT");
            outbox.setInReplyTo("MOVE " + objetivoX + " " + objetivoY + " " + objetivoZ);
            outbox.setContent("FOUND " + (int) jediXReal + " " + (int) jediYReal);
            this.LARVAsend(outbox);
        }
    }
    
    
    /*
    * @author Ahmed
    */
    // Inspecciona el thermal para comprobar si hay Jedis
    // si los hay usa otro metodo para notificarlo al destroyer
    private void buscarJedis() {
        int posI, posJ;
        double jediX, jediY, jediXReal, jediYReal;
        
        int[][] thermal = myDashboard.getThermal();
        posI = posJ = -1;
        for (int i = 0; i < thermal.length; i++) {
            for (int j = 0; j < thermal[i].length; j++) {
                if (thermal[i][j] == 0) {
                    
                    // Para obtener la coordenada real del objetivo que desprendio 0
                    // restamos 10, nuestra coordenada en el thermal. 

                    // Si detectamos al Jedi informamos
                    jediX = j - 10;
                    jediY = i - 10;

                    posI = i;
                    posJ = j;

                    // Sumamos ademas ahora el GPS
                    jediXReal = jediX + myGPS[0];
                    jediYReal = jediY + myGPS[1];
                    
                    // Notificar que se encontro un jedi
                    notificarJediEncontrado(jediXReal, jediYReal);
                }
            }   
        }

       
    }
    
     /*
    * @author Antonio
    * @author Ahmed
    */
    // El agente asciende volando hasta arriba mientras lo permita
    // la altura maxima de vuelo del mapa
    private boolean goToMaxFlight(){ 
        double miAltura = myGPS[2];
        
        // Mientras mi altura sea inferior a la altura maxima
        while (miAltura < maxFlight) {
            
            // Ejecutar peticion de execute accion de subir
            if (!myExecuteAction("UP")) {
                Error("INTENTANDO IR A LA MAXIMA ALTURA " + miAltura + " " + maxFlight);
                return false;
            }
            
            miAltura = myGPS[2];
        }     
        
        return true;
    }
    
    /*
    * @author Jaime
    * @author Ahmed
    */  
    public Status MySolveProblem() {

        if (despegando) {
            if (!goToMaxFlight()) {
                return Status.CHECKOUT;
            }
            
            despegando = false;
        }
        
        // Recibiendo posicion a la que ir (MOVE X Y Z)
        open = this.LARVAblockingReceive();
        
        String pos;

        switch (open.getPerformative()) {
            case ACLMessage.QUERY_IF:
                // Obtenemos direccion objetivo
                objetivoX = Integer.parseInt(open.getContent().split(" ")[1]);
                objetivoY = Integer.parseInt(open.getContent().split(" ")[2]);
                objetivoZ = Integer.parseInt(open.getContent().split(" ")[3]);
                
                pos = objetivoX + " " + objetivoY + " " + objetivoZ;
                
                // Informamos que estamos de acuerdo en ir
                outbox = open.createReply();
                
                outbox.setConversationId(sessionKey);
                outbox.setContent(pos);
                
                // Si la posicion fue suscrita previamente, entonces mandamos
                // disconfirm si no esta contenida esta disponible confirm
                outbox.setPerformative(
                        casillasSuscritas.contains(pos) ? 
                                ACLMessage.DISCONFIRM : ACLMessage.CONFIRM);
                
                this.LARVAsend(outbox);
                
                return Status.SOLVEPROBLEM;
                
            case ACLMessage.SUBSCRIBE:
                
                // Obtenemos direccion objetivo
                objetivoX = Integer.parseInt(open.getContent().split(" ")[1]);
                objetivoY = Integer.parseInt(open.getContent().split(" ")[2]);
                objetivoZ = Integer.parseInt(open.getContent().split(" ")[3]);
                
                pos = objetivoX + " " + objetivoY + " " + objetivoZ;
                
                
                
                // Elaborar respuesta
                outbox = open.createReply();
                outbox.setConversationId(sessionKey);
                outbox.setContent(pos);
                
                // Confirmar si se va a introducir o nos
                boolean contenida = casillasSuscritas.contains(pos);
                if (contenida) {
                    outbox.setPerformative(ACLMessage.REFUSE);
                } else {
                    casillasSuscritas.add(pos);
                    outbox.setPerformative(ACLMessage.CONFIRM);
                }
                
                return Status.SOLVEPROBLEM;
                
            case ACLMessage.CANCEL:
                Info("HE RECIBIDO CANCEL");
        
                // Buscar en el contenido alguna de las palabras clave
                boolean salir = false;
                for (String c: content.split(" ")) {
                    if (c.toUpperCase().equals("CREW") || c.toUpperCase().equals("CANCEL")) {
                        salir = true;
                        break;
                    }
                }
                
                // Si toca salir, hacemos checkout
                if (salir){
                    Info("TRABAJO COMPLETADO, SALIMOS");
                    return Status.CHECKOUT;
                } else {
                    // Obtenemos direccion objetivo
                    objetivoX = Integer.parseInt(open.getContent().split(" ")[1]);
                    objetivoY = Integer.parseInt(open.getContent().split(" ")[2]);
                    objetivoZ = Integer.parseInt(open.getContent().split(" ")[3]);

                    pos = objetivoX + " " + objetivoY + " " + objetivoZ;
                    
                    casillasSuscritas.remove(pos);
                    
                    return Status.SOLVEPROBLEM;
                }
                
            default:
                Error("PETICION NO RECONOCIDA O NO ESPERADA");
                return Status.SOLVEPROBLEM;
        }
    }
    
    /*
    * @author Jaime
    */  
    private void updatePosition(String action){
        switch(action){
            case "MOVE":
                switch(myAngular){
                    case 0:
                        objetivoX+=1;
                        break;
                    case 90:
                        objetivoY-=1;
                        break;
                    case 180:
                        objetivoX-=1;
                        break;
                    case 270:
                        objetivoY+=1;
                        break;
                        
                }
                break;
            case "LEFT":
                myAngular+=45;
                myAngular%=360;
                break;
            case "RIGHT":
                myAngular-=45;
                myAngular%=360;
                break;
            case "UP":
                objetivoZ=+5;
                break;
            case "DOWN":
                objetivoZ-=5;
                break;
        }
    }

    /*
    * @author Ahmed
    */
    // Devuelve true si el agente esta posado justamente sobre el suelo
    // false en caso contrario. Se supone que si la altura de la casilla
    // en la que esta el agente y la altura del agente son la misma
    // entonces esta posado sobre al suelo (a nivel del suelo)
    private boolean estaSobreElSuelo() {
        return (
            myGPS[2] ==                 // Altura en Z
            myDashboard.getMapLevel(                   // Map level
                (int) myGPS[0],     // Coordenada X
                (int) myGPS[1]      // Coordenada Y
            ));
    }
    
    /*
    *@author Ahmed
    */
    // Pide recarga al destroyer y espera la respuesta
    // actualiza ademas el estado pertinente
    private void pedirRecarga(){
        //------------------------------------------------
        // Crear respuesta
        outbox = open.createReply();
        
        // Indicar performativas e id
        outbox.setPerformative(ACLMessage.QUERY_IF);
        outbox.setConversationId(sessionKey);
            
        // Contenido
        outbox.setInReplyTo("MOVE " + objetivoX + " " + objetivoY + " " + objetivoZ);
        outbox.setContent("RECHARGE");
        outbox.setOntology("COMMITMENT");
        recargaPedida = true;
                    
        // Enviar
        this.LARVAsend(outbox);
        
        
        // Esperar a recibir respuesta 
        open = this.LARVAblockingReceive();
        if (open.getPerformative() == ACLMessage.CONFIRM) {
            recargando = true;
            
            Alert("TIEFIGHTER: NOS HAN CONCEDIDO LA RECARGA");
        } else {
            recargando = false;
        }
    }
    
    
    /*
    * @author Ahmed
    */
    // Metodo que devuelve el angulo del agente 
    // con respecto a un punto P pasado. Neccesita 
    // tener actualizado el valor del GPS. 
    // Es geometria pura y dura adaptada a Larva
    private double myGetAngular(Point p) {
        double [] getGPS = myGPS;
        
        Vector Norte = new Vector(new Point(0, 0), new Point(0, -10));
        Point me = new Point(getGPS[0], getGPS[1], getGPS[2]);
        Vector Busca = new Vector(me, p);
        
        int v = (int) Norte.angleXYTo(Busca);;
        v = 360+90-v;   
        return v%360;  
    }
    
    /* 
    * @author Jaime
    * @author Antonio
    * @author Ahmed
    */  
    private String myTakeDecision2(){
        String nextAction = "";
        Point p = new Point(objetivoX,objetivoY);

        if (!recargaPedida && myEnergy < umbralLimiteRecarga) {
            pedirRecarga();
        }

        // Si estamos recargando
        if (recargando) {
            
            Info("\n\n\n\n");
            Info("ESTAMOS YENDO A RECARGAR");
            Info("\n\n\n\n");
            
            // Bajamos al suelo para ejecutar la accion de recarga
//            if (myDashboard.getLidar()[5][5] > 0) {

            
            // Si no estamos sobre el suelo bajamos, 
            // en otro caso recargamos y reseteamos estado
            if (!estaSobreElSuelo()) {
                nextAction = "DOWN";
                
            } else {
                nextAction = "RECHARGE";
                recargando = false;
                recargaPedida = false;
            }
        } else {
            
            alturaTie = objetivoZ; //aqui recibe la que le diga el destroyer
            double alturaActual = myGPS[2];
        
            if(alturaActual == alturaTie){
//                final double angular = this.myDashboard.getAngular(p);
                final double angular = myGetAngular(p);
                
                double miAltura = myGPS[2];

                double distanciaAngulo = (angular - compass + gradoTotal) % gradoTotal;
        //        Info("\n\n\nDistanciaAngulo: " + distanciaAngulo);
        //        Info("\n\n\n");
        //        Info("\n\n\nAngular: " + angular);
        //        Info("\n\n\n");
        //        Info("\n\n\nCompass: " + compass);
        //        Info("\n\n\n");
                if( distanciaAngulo >= 45) {

                    // Elegir distancia de giro minimo
                    if ( distanciaAngulo < gradoTotal/2 ) {
                        nextAction = "LEFT";
                        compass = (compass + 45 + gradoTotal) % gradoTotal;
                    }
                    else {
                        nextAction = "RIGHT";
                        compass = (compass - 45 + gradoTotal) % gradoTotal;
                    }
                }else{
                    nextAction = "MOVE";
                }
            }
            else {
                // Si la altura nos coincide, calculamos si subir o bajar
                if(alturaActual < alturaTie){
                    nextAction = "UP";
                }
                else if(alturaActual > alturaTie){
                    Info("HAGO DOWN: " + alturaActual + ", " + alturaTie);
                    nextAction = "DOWN";
                }
            } 
        }
        
        return nextAction;
    }

    
    private Boolean estaCasillaProhibida(double[] posicion){
        Info("\t Casilla enfrente: X" + posicion[0] + " Y: " + posicion[1]);
        for(int i=0; i<casillasProhibidas.size(); i++){
            Info("\t Casilla prohibida: X" + casillasProhibidas.get(i)[0] + " Y: " + casillasProhibidas.get(i)[1]);
            if(casillasProhibidas.get(i)[0] == posicion[0] && casillasProhibidas.get(i)[1] == posicion[1]){
                return true;
            }
        }
        return false;
    }
    
    private double[] casillaEnfrente(int compass, double[] gps){
        double[] casillaFinal = gps;
        
        if (compass >= 0 && compass < 45) {
            casillaFinal[0]++;
        } else if (compass >= 45 && compass < 90) {
            casillaFinal[1]--;
            casillaFinal[0]++;
        } else if (compass >= 90 && compass < 135) {
            casillaFinal[1]--;
        } else if (compass >= 135 && compass < 180) {
            casillaFinal[1]--;
            casillaFinal[0]--;
        } else if (compass >= 180 && compass < 225) {
            casillaFinal[0]--;
        } else if (compass >= 225 && compass < 270) {
            casillaFinal[1]++;
            casillaFinal[0]--;
        } else if (compass >= 270 && compass < 315) {
            casillaFinal[1]++;
        } else if (compass >= 315 && compass < 360) {
            casillaFinal[1]++;
            casillaFinal[0]++;
        }
        
        return casillaFinal;  
    }
    
    // Metodo privado que devuelve la altura de la casilla que se encuentre
    // en la direccion que apunte el compass (sobre el lidar pasado)
    private int mapearAlturaSegunAngulo (final int angulo, final int lidar [][]) {
        // Hallar altura casilla de enfrente
        int alturaBuscada = -1;
//        switch(angulo){
//            case 0:     alturaBuscada = lidar[5][6]; break;
//            case 45:    alturaBuscada = lidar[4][6]; break;
//            case 90:    alturaBuscada = lidar[4][5]; break;
//            case 135:   alturaBuscada = lidar[4][4]; break;
//            case 180:   alturaBuscada = lidar[5][4]; break;
//            case 225:   alturaBuscada = lidar[6][4]; break;
//            case 270:   alturaBuscada = lidar[6][5]; break;
//            case 315:   alturaBuscada = lidar[6][6]; break;
//            
//            default: Alert("Angulo no reconocido " + angulo); break;
//        }
//        
//        return alturaBuscada;
        
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
    
  
    
    // lee sensores mediante peticiones al sensorManager, si fue lectura 
    // correcta devuelve true, en otro caso devuelve false
    private boolean myReadSensors() {
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setContent("Query sensors session " + sessionKey);
        
        this.LARVAsend(outbox);
        Info("Request query sensors session to " + sessionManager);
        
        inbox = this.LARVAblockingReceive(new MessageTemplate(new MessageTemplate.MatchExpression() {
            @Override
            public boolean match(ACLMessage aclm) {
                return aclm.getSender().equals(new AID(sessionManager, AID.ISLOCALNAME));
            }
        }));
        
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();
        
        // Comprobar que la lectura fuese correcta
        if(inbox.getPerformative() == ACLMessage.REFUSE 
                || inbox.getPerformative() == ACLMessage.FAILURE) {
            Error(content);
            return false;
        }
        
        // ACTUALIZAR COSTE SENSORES
        myEnergy -= (mySensors.length * costeSensor);
        
        return true;
    }
    
    // Ejecuta una accion enviando un mensaje al session manager
    // Devuelve true en caso de que la accion se ejecutase correctamente
    // false en otro caso diferente
    private boolean myExecuteAction(String accion){
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setContent("Request execute " + accion + " session " + sessionKey);
        
        this.LARVAsend(outbox);
        Info("Request executing action " + accion + " to " + sessionManager);
        
        inbox = this.LARVAblockingReceive();
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();
        
        if (inbox.getPerformative() == ACLMessage.INFORM) {
            
            // Actualizar sensor de energia emulado
            actualizarSensorEnergy(accion);
            
            
            // Actualizar GPS emulado
            actualizarSensorGPS(accion);
            
            return true;
        } else {
            Info("MyExecuteAction: " + content);
            return false;
        }        
    }
    
    /*
    * @author Ahmed
    */
    // Actualizar sensor de energia en base a la accion que se ejecuta
    private void actualizarSensorEnergy(String accion){
        
        // ACTUALIZAR ENERGIA COMO ES DEBIDO
        if (accion.equals("RECHARGE")) {
            myEnergy = maxEnergy;
            Info("ENERGIA RECARGADA COMPLETAMENTE");
        }
        else {
                
            // En otro caso, tenemos que identificar cual fue 
            // la accion que se llevo a cabo y si es necesario
            // actualizar la energia o no
                
               

            // Busca y actualiza
            for (String s: accionesCobrables) {
                if (s.equals(accion)) {
                    myEnergy -= costeAccion;
                    break;
                }
            }
        }
    }
    
    /*
    * @author Ahmed
    */
    // Actualiza sensor GPS en base a accion para emularlo
    private void actualizarSensorGPS(String accion) {
        if (accion.equals("MOVE")) {
            double [] res = casillaEnfrente(compass, myGPS);
            myGPS = res;
                    
        } else if (accion.equals("UP")) {
            myGPS[2] += 5;
            
        } else if (accion.equals("DOWN")) {
            myGPS[2] -= 5;
        } else {
            Info("NADA QUE ACTUALIZAR: " + accion);
        }
    }
}
