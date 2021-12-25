package tiefighter;

import agents.LARVAFirstAgent;
import com.eclipsesource.json.JsonHandler;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonParser;
import geometry.Point;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.List;
import swing.LARVACompactDash;
import swing.LARVADash;

/*
* @author Ahmed
* @author Antonio
*/
public class Practica3Destroyer extends LARVAFirstAgent{

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, 
        COMISSIONING, JOINSESSION, SOLVEPROBLEM, 
        CLOSEPROBLEM, EXIT
    }
        
    Status mystatus;
    
    /*
    * @author Ahmed
    * @author Antonio
    */
    private final String password = "106-WING-8";   // Alias de nuestra session
    private int posAparicionX = 0;                  // Pos en la que aparecera el destroyer en X
    private int posAparicionY = 0;                  // Pos en la que aparecera el destroyer en Y
    
    
    private int anchoSensor = 10;
    private int movimientoX = anchoSensor * 2;
    private int alturaFighter;                      // Valor por defecto, cambiara luego
    private int alturaCorellian;                    // Valor por defecto, cambiara luego
    
    private String mapLevel;                        // Nivel del mapa
    
    /*
    * @author Ahmed
    * @author Antonio
    */
    // MUNDOS :
    //      Ando
    //      Bogano
    //      Coruscant
    //      DQar
    //      Erkit
    //      Fondor

    private ArrayList<String> fighters;
    private ArrayList<String> corellians;
    private ArrayList<String> razors;
    
    String service = "PManager", problem = "Fondor",
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
    
    // Umbral a partir del cual se girara para 
    // orientarse desde el compass al angular
    private final int umbralGiro = gradoTotal/8;
    
    // indica que estamos evitando un obstaculo
    private Boolean evitando = false;
    
    /*
    * @author Ahmed
    */
    private int maxAlturaSuelo;
    
    // Cada tie tendra su lista de encontrados pertinente
    private ArrayList<ArrayList<String>> encontrados = 
            new ArrayList<>(
                List.of(
                    new ArrayList<String>(),
                    new ArrayList<String>()
                )
            );
    
    private ArrayList<String> posicionesMove = 
            new ArrayList<>(
                    List.of("45 15 0",
                            "40 40 0",
                            "10 33 0", 
                            "24 23 0")
            );
    
    private boolean [] corellianOcupado = {true, true};      // Verdadero hasta que no se haga inform
    private boolean [] fighterCancelado = {false, false};    // Aun no cancelados
    private boolean [] corellianCancelado = { false, false}; // Aun no cancelados
 
    
    /*
    * @author Ahmed
    */
    private ArrayList<String> recorridoPrimerCuadrante;
    private ArrayList<String> recorridoSegundoCuadrante;
    private ArrayList<String> spawnPointsCorellians = new ArrayList<>();
    
    private boolean recharge, found, move, capture; // Posibles estados de una peticion
    
    /*
    * @author Ahmed
    */
    // Para control de recargas
    private int recargasDisponibles = 5;
    private int recargasRestantesFighters = 2;
    private int recargasRestantesCorellians = 3;
    
    
    private ArrayList<double[]> casillasProhibidas = new ArrayList<>();
    
    private ArrayList<String> acciones = new ArrayList<>();
    
    int width, height, maxFlight;
    
    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
//                "ALIVE",
//                "ONTARGET",   // No 
                "GPS",        // No
//                "COMPASS",
//                "LIDAR",
//                "ALTITUDE",   // No
//                "VISUAL",     // No
//                "ENERGY",
//                "PAYLOAD",
//                "DISTANCE",
//                "ANGULAR",
//                "THERMAL"     // No
            };
    
    boolean step = false;   // TEMPORAL

    @Override
    public void setup() {
        super.setup();
        logger.onOverwrite();
        logger.setLoggerFileName("mylog.json");
        logger.offEcho();
        
//        this.enableDeepLARVAMonitoring();
        Info("Setup and configure agent");
        mystatus = Status.CHECKIN;
        exit = false;
        
//        this.myDashboard = new LARVADash(LARVADash.Layout.DASHBOARD, this);
        this.myDashboard = new LARVADash(this);
//        doActivateLARVADash();

        
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
        
        if (!loadMyPassport("passport/MyPassport.passport")) {
            Error("Unable to load passport file");
            return Status.EXIT;
        }
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        /*
        * @author Ahmed
        * @author Antonio
        */
        
        // Nos registramos en el DF para que se nos pueda localizar
        // dentro de la sesion (todos los agentes deben hacerlo).
        DFSetMyServices(new String[]{"DESTROYER " + password});
        
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
        
        /*
        * @author Antonio
        * @author Ahmed
        */
        // Aniadimos campo alias para asi poder abrir un problema y que
        // otros se conecten a nuestra sesion con dicho alias
        outbox.setContent("Request open " + problem + " alias " + password);
        
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);
        
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            
            /*
            * @author Antonio
            * @author Ahmed
            */
            // Este session nos servira para comenzar a enviar performativas
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
            
            /*
            * @author Ahmed
            * @author Antonio
            */
            
            // Ponemos contenido del mensaje y tambien id de la conversacion
            outbox.setContent("Request product " + s + " session " + sessionKey);
            outbox.setConversationId(sessionKey);
            
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
        
        /*
        * @author Ahmed
        * @author Antonio
        */
        session.setContent("Request join session " + sessionKey 
                            + " as Destroyer at " + posAparicionX + " " + posAparicionY
                            + " attach sensors " + sensorKeys);
       
        
        session.setPerformative(ACLMessage.REQUEST);
        this.LARVAsend(session);
        
        // Esperar a obtener respuesta
        session = this.LARVAblockingReceive();
        
        
        String parse[] = session.getContent().split(" ");
        
        // session.getPerformative() == ACLMessage.CONFIRM
        if (session.getPerformative() == ACLMessage.CONFIRM) {
            // Es el ancho del mapa, el numero de columnas ----- X
            width = Integer.parseInt(parse[8]);
            
            // Es el alto del mapa, el numero de filas | Y
            height = Integer.parseInt(parse[10]);
            
            // Es la altura maxima a la que puede llegar un agente Z
            maxFlight = Integer.parseInt(parse[14]);
            
            // Ascender hasta el punto maximo del mapa
            goToMaxFlight();
            
            // Obtener mapa del nivel
            getMapaDelNivel();
            
            // Reclutar agentes para nuestro equipo
            getRecruitment();
                    
            // Desplazar agentes a posiciones y alturas predeterminadas
            initPosicionesAgentes();
            
            return Status.SOLVEPROBLEM;
        } else {
            Alert("Error: joining session: " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
    }
    
    /*
    * @author Antonio
    * @author Ahmed
    */
    
    // El agente asciende volando hasta arriba mientras lo permita
    // la altura maxima de vuelo permitida por el mapa
    private void goToMaxFlight(){ 
        myReadSensors();  
        double miAltura = myDashboard.getGPS()[2];
        
        // Mientras mi altura sea inferior a la altura maxima
        while (miAltura < maxFlight) {
            
            // Ejecutar peticion de execute accion de subir
            session = session.createReply();
            session.setContent("Request execute UP session " + sessionKey );
            
            session.setPerformative(ACLMessage.REQUEST);
            this.LARVAsend(session);
            
            // Esperamos a obtener resultado de la ejecucion
            session = this.LARVAblockingReceive();
            
            // Actualizamos los sensores y guardamos nuevamente 
            // el valor de la altura (a traves del GPS)
            myReadSensors();  
            miAltura = myDashboard.getGPS()[2];
        }       
    }
    
    /*
    * @author Antonio 
    * @author Ahmed
    */
    // Obtiene el mapa completo del nivel
    // Precondicion: la altura de vuelo debe ser la maxima
    private void getMapaDelNivel() {
        session = session.createReply();
        
        session.setPerformative(ACLMessage.QUERY_REF);
        session.setContent("Query MAP session " + sessionKey);
        session.setConversationId(sessionKey);
        
        this.LARVAsend(session);
        
        session = this.LARVAblockingReceive();
        
        mapLevel = session.getContent();
        
        myReadSensors();
        maxAlturaSuelo = 0;
        int alturaActual = 0;
        
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                alturaActual = this.myDashboard.getMapLevel(i, j);
                if (maxAlturaSuelo < alturaActual) maxAlturaSuelo = alturaActual;
            }
        }
        
        alturaCorellian = maxAlturaSuelo;            // Corellians iran sobre la altura del suelo
        alturaFighter = alturaCorellian + 5;         // Fighters iran justamente por encima
    }
        
    /*
    * @author Antonio
    * @author Raúl
    * @author Ahmed
    */
    // Metodo que genera los recorridos necesarios que va a llevar a cabo cada agente
    // dependeria del numero de agentes el barrido que se va a llevar a cabo
    private void generarBarrido(){
        recorridoPrimerCuadrante = this.getRecorridoPrimerCuadrante();
        recorridoSegundoCuadrante = this.getRecorridoSegundoCuadrante();
        
//        Alert(recorridoPrimerCuadrante.toString());
//        Alert(recorridoSegundoCuadrante.toString());
//        Alert("Este es el recorrido generado");
        
        // generarSpawnPointsCorellian()
    }
    
    /*
    * @author Ahmed
    */
    // Genera las posiciones de despliegue de lso corellian
    private void generarSpawnPointsCorellian() {
        int x, y, z;
        
        // Primer cuadrante o corellian
        x = Integer.parseInt(recorridoPrimerCuadrante.get(0).split(" ")[0]) + 5;
        y = Integer.parseInt(recorridoPrimerCuadrante.get(0).split(" ")[1]) + 5;
        z = alturaCorellian;
        
        spawnPointsCorellians.add(x + " " + y + " " + z);
        
        // Segundo cuadrante o corellian
        // Primer cuadrante o corellian
        x = Integer.parseInt(recorridoSegundoCuadrante.get(0).split(" ")[0]) + 5;
        y = Integer.parseInt(recorridoSegundoCuadrante.get(0).split(" ")[1]) + 5;
        z = alturaCorellian;
        
        spawnPointsCorellians.add(x + " " + y + " " + z);
        

    }
/*
    * @author Antonio
    * @author Raul
    */
    // Genera una secuencia ordenada de puntos de tal manera que
    // al seguirla se barre el primer cuadrante del mapa en zig-zag vertical
    private ArrayList<String> getRecorridoPrimerCuadrante(){
        int x = anchoSensor, y = anchoSensor;
        
        int distanciaRestante;
        
        ArrayList<String> puntos = new ArrayList<>();
        
        // Insertar punto inicial
        puntos.add(
                String.valueOf(x)
                + " " + 
                String.valueOf(y) 
                + " " + 
                String.valueOf(alturaFighter)
        );
        
        while(x < width/2){
            y = height - anchoSensor;
            
            puntos.add(
                    String.valueOf(x)  
                    + " " + 
                    String.valueOf(y) 
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
            distanciaRestante = width/2 - x;
            
            if(distanciaRestante <= movimientoX && distanciaRestante > anchoSensor){
                x += distanciaRestante - anchoSensor;
            }
            else
                 x += movimientoX;
            
            if(x > width/2)
                break;
            
            puntos.add(
                    String.valueOf(x)  
                    + " " + 
                    String.valueOf(y) 
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
            y = anchoSensor;
            
            puntos.add(
                    String.valueOf(x)
                    + " " + 
                    String.valueOf(y)
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
            distanciaRestante = width/2 - x;
            
            if(distanciaRestante <= movimientoX && distanciaRestante > anchoSensor){
                x += distanciaRestante - anchoSensor;
            }
            else
                 x += movimientoX;
            
            if(x > width/2)
                break;
            
            puntos.add(
                    String.valueOf(x)  
                    + " " + 
                    String.valueOf(y) 
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
        }
        
        return puntos;
        
    }
    
    /*
    * @author Antonio
    * @author Raul
    */
    private ArrayList<String> getRecorridoSegundoCuadrante(){
        int x = width/2 + anchoSensor, y = anchoSensor;
        
        int distanciaRestante;
        
        ArrayList<String> puntos = new ArrayList<>();
        
        // Insertar punto inicial
        puntos.add(
                String.valueOf(x)
                + " " + 
                String.valueOf(y) 
                + " " + 
                String.valueOf(alturaFighter)
        );
        
        while(x < width){
            y = height - anchoSensor;
            
            puntos.add(
                    String.valueOf(x)  
                    + " " + 
                    String.valueOf(y) 
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
            distanciaRestante = width - x;
            
            if(distanciaRestante <= movimientoX && distanciaRestante > anchoSensor){
                x += distanciaRestante - anchoSensor;
            }
            else
                 x += movimientoX;
            
            if(x > width)
                break;
            
            puntos.add(
                    String.valueOf(x)  
                    + " " + 
                    String.valueOf(y) 
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
            y = anchoSensor;
            
            puntos.add(
                    String.valueOf(x)
                    + " " + 
                    String.valueOf(y)
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
            distanciaRestante = width - x;
            
            if(distanciaRestante <= movimientoX && distanciaRestante > anchoSensor){
                x += distanciaRestante - anchoSensor;
            }
            else
                 x += movimientoX;
            
            if(x > width)
                break;
            
            
            puntos.add(
                    String.valueOf(x)  
                    + " " + 
                    String.valueOf(y) 
                    + " " + 
                    String.valueOf(alturaFighter)
            );
            
        }
        
        return puntos;
       
    }     
        
    
    
    /*
    * @author Antonio
    * @author Ahmed
    */
    private void getRecruitment (){
        Info("\n\n\n\n\n\n\n"); 
        fighters = this.DFGetAllProvidersOf("FIGHTER " + password);
        corellians = this.DFGetAllProvidersOf("CORELLIAN " + password);
        razors = this.DFGetAllProvidersOf("RAZOR " + password);
        ArrayList<String> agentes = new ArrayList<String>();
        
        // Rellenar vector de agentes con todos los posibles agentes
        for (String f: fighters)    agentes.add(f);
        for (String c: corellians)  agentes.add(c);
        for (String r: razors)      agentes.add(r);
        
        
        // Genera secuencias de barrido para cada Tie y posiciones iniciales
        generarBarrido();
        
        // Genera vector de puntos de despliegue en base al numero de corellians
        generarSpawnPointsCorellian();
        
        // Vamos a enviar un nuevo mensaje
        outbox = new ACLMessage(ACLMessage.CFP);
        outbox.setSender(getAID());
        
        // Los receptores del mensaje seran los que obtuvimos previamente
        int ncfp=0;
        for (String s : agentes) {
            outbox.addReceiver(new AID(s, AID.ISLOCALNAME));
            Info("Este agente es: " + s);
            ncfp++; // contar receptores enviados
            
        }
        
        // Decidimos enviar mapa, lo adjuntamos como contenido
        outbox.setContent(mapLevel);
        outbox.setConversationId(sessionKey);
        outbox.setOntology("COMMITMENT");
        outbox.setReplyWith("Recruit crew for session " + password);
        
        // Realizar envio de mensaje
        this.LARVAsend(outbox);
        
        // Esperar tantas respuestas como cfp a agente se hayan enviado        
        int ninf = 0;
        
        while (ncfp > 0) {
            // Esperar a que algun agente envie algo
            inbox = this.LARVAblockingReceive();
            
            // Si es un agree, entonces 
            if (inbox.getPerformative() == ACLMessage.AGREE) {
                
                Info("Recibiendo AGREE y mandando ACCEPT_PROPOSAL de " + inbox.getSender());
                
                // Extraemos nombre del agente que envia
                String sender = getSenderName(inbox.getSender());
                
                /*
                * @author Ahmed
                */
                // Coordenadas aleatorias por ahora
                int x = 1 + ncfp;
                int y = x;
                
                // Coordenadas temporales, por si algun error se diese
                String spawnPos = "" + x + " " + y;
                
                // Detectar que agente mando el mensaje y en base a eso
                // desplegarlo en un punto del mundo u otro
                int index = fighters.indexOf(sender);
                if (index >= 0) {
                    Info("HEMOS ENCONRTRADO UN FIGHTER");
                    
                    if (index == 0){
                        spawnPos = recorridoPrimerCuadrante.get(0);
                    } else if (index == 1) {
                        spawnPos = recorridoSegundoCuadrante.get(0);
                    } else {
                        Error("TIEFIGHTER NO ESPERADO");    
                    }
                    
                } else {
                    index = corellians.indexOf(sender);
                    
                    if (index >= 0) spawnPos = spawnPointsCorellians.get(index);
                    else Error("AGENTE NO CORELLIAN O NO REGISTRADO: " + inbox.getSender());
                }
                
                outbox = inbox.createReply();
                outbox.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                outbox.setOntology("COMMITMENT");
                
                outbox.setContent(spawnPos);
                outbox.setConversationId(sessionKey);
                outbox.setReplyWith("TAKEOFF " + password);
                this.LARVAsend(outbox);   
                
                ncfp--; // Hemos recibido una respuesta a cfp, uno menos
                ninf++; // Esperamos un nuevo inform de respuesta al accept_propsal
                
            } else if (inbox.getPerformative() == ACLMessage.INFORM){
                // Respuesta al accept_proposal
                Info("INFORM RECIBIDO EN RECRUITMENT: " + inbox.getContent() + " sender: " + inbox.getSender());
                ninf--; // Hemos recibido un info, uno menos por recibir                
            } else {
                Error("RECIBIDO MENSAJE INESPERADO: " + inbox.getPerformative());
            }
        }
        
        while (ninf > 0) {
            inbox = this.LARVAblockingReceive();
            Info("INFORM RECIBIDO ESPERANDO INFORMS: " + inbox.getContent() + " sender: " + inbox.getSender());
            ninf--; // Hemos recibido un info, uno menos por recibir 
        }     
    }
    
    /*
    * @author Ahmed
    */
    // Recibe un sender AID y lo procesa para 
    // sacar de este la cadena relativa al nombre
    private String getSenderName(final AID sender) {
        // Extraemos nombre del agente que envia
        String res = sender.getName().split("@")[0];
        return res;
    }
   
    /**
    * @author Ahmed
    */
    // Metodo que desplaza a cada agente a 
    // la posicion deseada al principio de la partida
    private void initPosicionesAgentes() {
        
        // VAMOS A ENVIAR MENSAJES
        outbox = new ACLMessage();
        outbox.setSender(getAID());
            
        //--------------------------------------------------------------------//
        // TIEFIGHTERS
        // Hacer primer request move para comenzar 
        // a mover al tiefighter a la posicion que queremos y 
        // principalmente hacer que este a la altura adecuada
        
        // Si tenemos algun fighter
        if (fighters.size() > 0) {
            
            outbox.addReceiver(new AID(fighters.get(0), AID.ISLOCALNAME));
            
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setContent("MOVE " + recorridoPrimerCuadrante.get(0));
            outbox.setReplyWith("MOVE " + recorridoPrimerCuadrante.get(0));
            
            outbox.setOntology("COMMITMENT");
            outbox.setConversationId(sessionKey);                
            
            // Realizar envio de mensaje
            this.LARVAsend(outbox);
            
            // Si tenemos otro, le mandamos tambien 
            // la informacion de spawn y location
            if (fighters.size() > 1) {
                outbox.clearAllReceiver();
                outbox.addReceiver(new AID(fighters.get(1), AID.ISLOCALNAME));
            
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setContent("MOVE " + recorridoSegundoCuadrante.get(0));
                outbox.setReplyWith("MOVE " + recorridoSegundoCuadrante.get(0));

                outbox.setOntology("COMMITMENT");
                outbox.setConversationId(sessionKey);                

                // Realizar envio de mensaje
                this.LARVAsend(outbox);
            }
        }
        
        //--------------------------------------------------------------------//
        // CORELLIANS
        // Mover agente a la posicion que se le asigno previamente 
        // teniendo en cuenta ahora x, y, z. Lo que realmente queremos 
        // es que suba
        
        // Si tenemos algun fighter
        if (corellians.size() > 0) {
            outbox.clearAllReceiver();
            outbox.addReceiver(new AID(corellians.get(0), AID.ISLOCALNAME));
            
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setContent("MOVE " + spawnPointsCorellians.get(0));
            outbox.setReplyWith("MOVE " + spawnPointsCorellians.get(0));
            
            outbox.setOntology("COMMITMENT");
            outbox.setConversationId(sessionKey);                
            
            // Realizar envio de mensaje
            this.LARVAsend(outbox);
            
            // Si tenemos otro, le mandamos tambien 
            // la informacion de spawn y location
            if (corellians.size() > 1) {
                outbox.clearAllReceiver();
                outbox.addReceiver(new AID(corellians.get(1), AID.ISLOCALNAME));
            
                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setContent("MOVE " + spawnPointsCorellians.get(1));
                outbox.setReplyWith("MOVE " + spawnPointsCorellians.get(1));

                outbox.setOntology("COMMITMENT");
                outbox.setConversationId(sessionKey);                

                // Realizar envio de mensaje
                this.LARVAsend(outbox);
            }
        }        
    }

    private String myTakeDecision() {
        String nextAction = "";
        
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
                        final double [] miCasilla = this.myDashboard.getGPS();
                        double miAltura = myDashboard.getGPS()[2];

                        
                        double distanciaAngulo = (angular - compass + gradoTotal) % gradoTotal;
                        if( distanciaAngulo >= umbralGiro && !evitando) {
                            
//                            Alert("ME ORIENTO HACIA ANGULAR, DISTANCIA: "+ distanciaAngulo);
                            
                            // Elegir distancia de giro minimo
                            if ( distanciaAngulo < gradoTotal/2 ) {
                                 nextAction = "LEFT";
                            }
                            else {
                                 nextAction = "RIGHT";
                            }
                        } else {
//                            Alert("O ESTOY APUNTANDO O ESTOY EVITANDO");
                            
                            // Creacion de variables para evitar repetir llamadas 
                            // a funciones y mejorar asi la eficiencia
                            int alturaEnfrente = mapearAlturaSegunAngulo(compass, lidar);
                            double [] casillaEnfrente = getCasillaSegunAngulo(compass, this.myDashboard.getGPS());
                            boolean casillaEnfrenteProhibida = 
                                    estaCasillaProhibida(casillaEnfrente, "COMPASS");

                            // Si enfrente es mas alto que dron hay que subir
                            if (alturaEnfrente < 0 || casillaEnfrenteProhibida){
//                                Alert("CASILLA ENFRENTE ESTA PROHIBIDA O ES MAS ALTA QUE YO");
                                if(casillaEnfrenteProhibida){
                                    nextAction = "LEFT";
                                    evitando = true;
                                    
                                }else if (miAltura == maxFlight) {
                                    nextAction = "LEFT";
                                    evitando = true;
                                                                        
                                    if(!estaCasillaProhibida(myDashboard.getGPS(), "MAXFLIGHT")){
                                        casillasProhibidas.add(myDashboard.getGPS());
                                    }
                                } else {
                                    nextAction = "UP";
//                                    evitando = false;
                                }
                            } else {
                                // ANOTACION:
                                // Si estamos aqui es porque en el if anterior 
                                // se ha demostrado que lo que hay enfrente nuestra
                                // es una casilla valida a la que podemos ir
                                nextAction = "MOVE";
//                                Alert("LA CASILLA DE ENFRENTE NO ES MAS ALTA NI ESTA PROHIBIDA");
                                
                                // PSEUDOCODIGO: if evitando then nextAction : "move"
                                if (evitando) {
                                        double objetivo = -1;
                                        
                                        
                                        final int incremento = 45;
                                        int angulo = 0;
                                        boolean esAccesible = false;
                                        
                                        double casilla[];
                                        double alturaLidar;
                                        
                                        // Buscar una casilla accesible y lo mas cercana 
                                        // al angulo que nos indica el angular
                                        while (angulo < gradoTotal && !esAccesible) {
                                            objetivo = (angular + angulo) % gradoTotal;
                                            
                                            casilla = getCasillaSegunAngulo( objetivo, myDashboard.getGPS());
//                                            Alert("grado objetivo: " + objetivo + " casilla actual: " + casilla[0] + ":" + casilla[1]);
                                            alturaLidar = mapearAlturaSegunAngulo(objetivo, lidar);
                                            esAccesible =
                                                    (!estaCasillaProhibida(casilla, "BUCLE") && 
                                                    casilla[2] <= maxFlight && 
                                                    alturaLidar >= 0);
                                            
                                            angulo += incremento;
                                            
                                            Info("\n\nSigo en bucle buscando un angulo de giro interesante");
                                        }
                                        
                                        if (esAccesible) {
//                                            evitando = false; // DESCOMENTAR PARA TENER VIEJA VERSION

                                            if (objetivo == angular) {
                                                evitando = false;
                                                
                                                infoCasillasProhibidas();
//                                                Alert("COINCIDEN OBJ Y ANG FALSE EVITAR " + casillasProhibidas.size());

                                            } else {
//                                                Alert("CASILLA ANIADIDA POR NO PODER IR A ANGULAR");    
                                                if(!estaCasillaProhibida(myDashboard.getGPS() , " ")){
                                                    casillasProhibidas.add(myDashboard.getGPS());
                                                }   
                                            }
                                            
                                            if (distanciaEntreAngulos(objetivo, compass) >= umbralGiro) {
                                                nextAction = orientarnosHaciaAngulo(objetivo, compass);
                                            } else {
                                                nextAction = "MOVE";
                                            }


    //                                            Alert("ACCESIBLE--> ACCION ES: " + nextAction);
    
                                                                                            //                                            Alert("OBJETIVO ES: " + objetivo + " ANGULO ES: " + angulo);


                                        }
                                        
                                        
                                }
                                
                                
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
        
        
        
        return nextAction;
    }
    
    
    // Devuelve la distancia entre dos angulos
    private double distanciaEntreAngulos(final double a1, final double a2) {
//        return (angular - compass + gradoTotal) % gradoTotal;
        return (a1 - a2 + gradoTotal) % gradoTotal;
    }
    
    // Devuelve el sentido de giro que conlleve menos acciones
    private String orientarnosHaciaAngulo(final double destino, final double origen) {
        double dist = distanciaEntreAngulos(destino, origen);
        
        if ( dist < gradoTotal/2 ) return "LEFT";
        else                       return "RIGHT";
    }
    // Devuelve true si la posicion pasada esta en 
    // el vector de prohibidas false en otro caso
    private Boolean estaCasillaProhibida(double[] posicion, String msg){
//        Info("\t Casilla enfrente: X" + posicion[0] + " Y: " + posicion[1]);
        
        // Se ha limitado tamanio maximo de la 
        // busqueda 5 casillas, se revisan las 5 finales
//        final int tamMaximoCasillas = 15;
//        int inicio = casillasProhibidas.size() - tamMaximoCasillas;
//        if (inicio < 0) inicio = 0;
        
        for(int i = casillasProhibidas.size() - 1; i >=0; i--){
//            Info("\t Casilla prohibida: X" + casillasProhibidas.get(i)[0] + " Y: " + casillasProhibidas.get(i)[1]);
            if(casillasProhibidas.get(i)[0] == posicion[0] && casillasProhibidas.get(i)[1] == posicion[1]){
//                Alert(msg + ": HEMOS ENCONTRADO CASILLA PROHIBIDA X:" + posicion[0] + " Y: " + posicion[1]);
                return true;
            }
        }
        return false;
    }
    
    private void infoCasillasProhibidas() {
         for(int i = casillasProhibidas.size() - 1; i >=0; i--){
            Info("\t Casilla prohibida: X" + casillasProhibidas.get(i)[0] + " Y: " + casillasProhibidas.get(i)[1]);
         }
    }
    
    // Antiguo metodo getCasillaSegunAngulo, renombrado, hace lo mismo
    private double[] getCasillaSegunAngulo(double angulo, double[] gps){
        double[] casillaFinal = gps;
        
        if (angulo >= 0 && angulo < 45) {
            casillaFinal[0]++;
        } else if (angulo >= 45 && angulo < 90) {
            casillaFinal[1]--;
            casillaFinal[0]++;
        } else if (angulo >= 90 && angulo < 135) {
            casillaFinal[1]--;
        } else if (angulo >= 135 && angulo < 180) {
            casillaFinal[1]--;
            casillaFinal[0]--;
        } else if (angulo >= 180 && angulo < 225) {
            casillaFinal[0]--;
        } else if (angulo >= 225 && angulo < 270) {
            casillaFinal[1]++;
            casillaFinal[0]--;
        } else if (angulo >= 270 && angulo < 315) {
            casillaFinal[1]++;
        } else if (angulo >= 315 && angulo < 360) {
            casillaFinal[1]++;
            casillaFinal[0]++;
        } else {
            Alert("ERROR: getCasillaSegunAngulo, no se detecto el rango");
        }
            
        
        return casillaFinal;  
    }
    
    // Metodo privado que devuelve la altura de la casilla que se encuentre
    // en la direccion que apunte el compass (sobre el lidar pasado)
    private int mapearAlturaSegunAngulo (final double angulo, final int lidar [][]) {
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
    
    /*
    * @author Ahmed
    */
    // Cundo se recibe un mensaje de peticion de recarga
    // se ejecuta este metodo. Identifica el emisor del mensaje
    // y comprueba si tiene recargas disponibles o no.
    // Si las tiene se las concede y actualiza el estado interno del
    // control de recargas, en otro caso las deniega
    private void procesarSolicitudRecarga(){
        int index = -1;
        
        // Crear respuesta y comprobar estado de recargas 
        // para discernir el resultado contenido
        outbox = inbox.createReply();
        outbox.setConversationId(sessionKey);
                    
        // Llevar a cabo control de recarga
        if (recargasDisponibles > 0) {
            index = fighters.indexOf(getSenderName(inbox.getSender()));
            if (index >= 0) {
                // Es un tieFighter, vamos a gestionar sus recargas
                
                // Comprobar recargas restantes 
                if (recargasRestantesFighters > 0) {
                    outbox.setPerformative(ACLMessage.CONFIRM);
                    recargasRestantesFighters--;
                } else {
                    outbox.setPerformative(ACLMessage.DISCONFIRM);
                }
            } else {
                index = corellians.indexOf(getSenderName(inbox.getSender()));
                if (index >= 0) {
                    // Es un corellian, vamos a gestionar sus recargas
                    // Comprobar recargas restantes 
                    if (recargasRestantesCorellians > 0) {
                        outbox.setPerformative(ACLMessage.CONFIRM);
                        recargasRestantesCorellians--;
                    } else {
                        outbox.setPerformative(ACLMessage.DISCONFIRM);
                                    
                        /*  // Posible mejora, si un fighter asociado que fue
                            // cancelado, entonces nos quedamos con su recarga
                            if (fighterCancelado[index] && recargasRestantesFighters > 0){
                                outbox.setPerformative(ACLMessage.CONFIRM);
                                recargasRestantesFighters--;
                            }
                        */
                    }
                }
            }
        }
        
        // Sea cual sea el contenido 
        // del mensaje debemos mandarlo
        this.LARVAsend(outbox);
    }
    
    
    /*
    * @author Ahmed 
    * @author Antonio
    * @author Raul
    * @author Jaime
    */
    // Metodo principal encapsula toda la logica del agente y 
    // es lo principal que hace
    public Status MySolveProblem() {
        /*
        * @author Jaime
        * @author Antonio
        * @author Ahmed
        */
        
        String content;
        
        // Nos basamos en una arquitectura cliente / servidor
        // Esperamos recibir una peticion y la procesaremos dando la respuesta 
        // adecuada
        inbox = this.LARVAblockingReceive();
        
        /*
        * @author Ahmed
        */
        
        int index = -1;
        
        switch(inbox.getPerformative()){
            case ACLMessage.INFORM:
                // Recibimos informacion y procesamos contenido para hallar de que
                // No respondemos nada simplemente mostramos por pantalla 
                // y a otra cosa
                content = inbox.getContent();
                move = capture = false;
                
                // Actualiza el tipo de inform recibido (move, capture, etc)
                actualizarTipoInform(content);
                
                if (move) {
                    // Si hemos detectado un inform move
                    Info("HE RECIBIDO UN INFORM MOVE: " + inbox.getContent() + 
                        " sender: " + inbox.getSender());
                    
                    // Obtenemos nombre del origen
                    String sender = getSenderName(inbox.getSender());
                    
//                    Alert("SENDER ES: " + sender);
                    
                    // Comprobamos quien es el agente que nos lo envio
                    index = fighters.indexOf( sender );
                    if (index >= 0) {
                        // Determinar el recorrido que se va a usar
                        // dependiendo del tie localizado
                        ArrayList<String> recorrido = 
                                (index == 0) ? 
                                recorridoPrimerCuadrante : 
                                recorridoSegundoCuadrante;
                        
//                        Alert("HEMOS ENCONTRADO TIEFIGHTER: " + sender);
                        
                        // Elminamos la posicion a la que le mandamos previamente moverse
                        recorrido.remove(0);

                        // Creamos una respuesta
                        outbox = inbox.createReply();

                        if (recorrido.size() > 0) {
                            // si nos quedan posiciones para movernos a ellas
                            // Movernos a la siguiente
                            outbox.setPerformative(ACLMessage.REQUEST);
                            outbox.setContent("MOVE " + recorrido.get(0));
                            outbox.setReplyWith("MOVE " + recorrido.get(0));
                            outbox.setOntology("COMMITMENT");
                            outbox.setConversationId(sessionKey);   

                            // Realizar envio de mensaje
                            this.LARVAsend(outbox);
//                            Alert("SE MOVERA A: " + recorridoPrimerCuadrante.get(0));
                            
                        } else {
                            // toca hacer CANCEL PORQUE YA NO HAY MAS
                            outbox.setPerformative(ACLMessage.CANCEL);
                            outbox.setOntology("COMMITMENT");
                            outbox.setConversationId(sessionKey); 
                            outbox.setContent("CANCEL CREW " + password);

                            fighterCancelado[index] = true;
                            this.LARVAsend(outbox);
                        }
                    } else {
                        // Vamos a buscar un corellian porque no hemos encontrado ningun tiefighter
//                        Alert("NO HEMOS ENCONTRADO UN TIEFIGHTER, BUSCAMOS UN CORELLIAN");
                        
                        // Si no era un fighter comprobamos a ver los corellian
                        index = corellians.indexOf(sender);
                        if (index >= 0){
                            corellianOcupado[index] = false;
//                            Alert("HEMOS ENCONTRADO CORELLIAN: " + sender);
                        } else {
                            Error("CORELLIAN NO RECONOCIDO");
                        }
                    }
                  } else if (capture) {
                    Info("HE RECIBIDO UN INFORM CAPTURE: " + inbox.getContent() + 
                        " sender: " + inbox.getSender());
                     
                    String sender = getSenderName(inbox.getSender());
                    index = corellians.indexOf(sender);
                    
                    if (index >= 0) {
                        encontrados.get(index).remove(0);    // Eliminar el elemento que fue encontrado
                        corellianOcupado[index] = false;     // Marcar captura a false
                    } else {
                        Error("CORELLIAN NO RECONOCIDO EN INFORM CAPTURE");
                    }
                    
                    
                }
                
                break;
                
            case ACLMessage.AGREE: 
                Info("HE RECIBIDO AGREE DE: " + getSenderName(inbox.getSender()));
                
//                Alert("Dentro del agree, este es el sender: " + inbox.getSender());
                break;
                
            case ACLMessage.FAILURE: 
                Info("HE RECIBIDO UN FAILURE DE CAPTURE " + inbox.toString());
                Alert("RECIBIDO FAILURE: " + inbox.toString());
                
                String agenteFallido = null;
                
                index = corellians.indexOf(getSenderName(inbox.getSender()));
                if (index >= 0) {
                    corellianCancelado[index] = true;
                    corellianOcupado[index] = false;    // No esta ocupado, esta listo para morir
                    agenteFallido = corellians.get(index);
                    
                } else {
                    index = fighters.indexOf(getSenderName(inbox.getSender()));
                    if (index >= 0) {
                        fighterCancelado[index] = true;
                        agenteFallido = fighters.get(index);
                    } 
                }
                
                // Cancelar agente si es que lo hemos detectado
                if (agenteFallido != null) {
                    outbox = new ACLMessage();
                    outbox.setSender(getAID());

                    outbox.addReceiver(new AID(agenteFallido, AID.ISLOCALNAME));
                    outbox.setPerformative(ACLMessage.CANCEL);
                    outbox.setOntology("COMMITMENT");
                    outbox.setConversationId(sessionKey); 
                    outbox.setContent("CANCEL CREW " + password);
                    this.LARVAsend(outbox);
                }
                else {
                    Error("RECIBIDO FAILURE DE AGENTE NO RECONOCIDO: " + inbox.getSender());
                }
                
                break;
                
            case ACLMessage.QUERY_IF: 
                // Recibimos una peticion de recarga
                Info("RECIBIDA PETICION DE RECARGA de " + inbox.getSender() + 
                        " CONTENIDO: " + inbox.getContent());
                
                content = inbox.getContent();
                recharge = false;
                
                for (String c: content.split(" ")) {
                    if (c.toUpperCase().equals("RECHARGE")) 
                        recharge = true;
                        break;
                }
                
                // Si el mensaje era del tipo recarga
                if (recharge) {
                    
                    procesarSolicitudRecarga();
                    
                    
                    

                    
                    
                    
                    Info("HEMOS DADO RECARGA A " + inbox.getSender());
                }
                
                // if (recharge) { hacer lo siguiente... }
                // else { envio not understood ... }
                
                // Debemos saber quien nos la envia
                // inbox.getSender();
                // Buscar en vector de reclutados a este agente
                // decidir si se la damos o no
                
                
                break;
                
            case ACLMessage.INFORM_REF:
                Info("RECIBIDA PETICION DE FOUND VE A CAPTURAR");
                content = inbox.getContent();
                found = false;
                
                String [] contentSplit = content.split(" ");
                
                for (String c: contentSplit) {
                    if (c.toUpperCase().equals("FOUND")) 
                        found = true;
                        break;
                }
                
                /*
                * @author Ahmed
                */
                // Se confirma que nos enviaron un found
                if (found) {
                    
                    // Hemos obtenido una nueva posicion
                    String x, y, z;
                    x = contentSplit[1];    // Sacamos X
                    y = contentSplit[2];    // Sacamos Y
                    z = "" + myDashboard.getMapLevel(
                            Integer.parseInt(x), 
                            Integer.parseInt(y)
                    );
                    
                    index = fighters.indexOf(
                            getSenderName(inbox.getSender())
                    );
                    
                    if (index < 0) {
                        Error("FIGHTER NO RECONOCIDO EN FOUND");
                    } else {
                        // Aniadimos a lista de encontrados
                        encontrados.get(index).add(x + " " + y + " " + z);                        
                        Info("HEMOS ENCONTRADO UN JEDI: " + x + " " + y + " " + z);
                    }
                }
                else {
                    Error("Not understood");
                    outbox = inbox.createReply();
                    outbox.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                 
                    // Realizar envio de mensaje
                    this.LARVAsend(outbox);
                }
                
                break;
                
            default: 
                Error("NO RECONOCEMOS LA PETICION");
                Info(inbox.getPerformative() + " " + inbox.getContent());
                
                outbox = inbox.createReply();
                outbox.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                 
                // Realizar envio de mensaje
                this.LARVAsend(outbox);
                
        }
        
        /*
        * @author Ahmed
        */
        // Si disponemos de corellians y los conocemos
        
        for (int i = 0; i < corellians.size(); i++) {
            
            
            // Comprobar cada corellian y sus datos almacenados
            if (!corellianOcupado[i] && !encontrados.get(i).isEmpty()){
                
                corellianOcupado[i] = true;
                outbox = new ACLMessage();
                outbox.setSender(getAID());
                outbox.addReceiver(new AID(corellians.get(i), AID.ISLOCALNAME));

                outbox.setPerformative(ACLMessage.REQUEST);
                outbox.setContent("CAPTURE " + encontrados.get(i).get(0));
                outbox.setReplyWith("CAPTURE " + encontrados.get(i).get(0));
                outbox.setOntology("COMMITMENT");
                outbox.setConversationId(sessionKey);
                
//                Alert("CORELLIAN ENVIADO A CAPTURAR");

                // Realizar envio de mensaje
                this.LARVAsend(outbox);
            }   
            
            
        }
        
        /*
        * @author Ahmed
        */
        // Si ya no podemos descubrir mas jedis (cancelamos los tie)
        // entonces, en cuanto acabemos de capturar los jedis conocidos
        // cancelamos los corellian (nos aseguramos que no hay mas encontrados)
        
        for (int i = 0; i < corellians.size(); i++){
            if (fighterCancelado[i] && !corellianOcupado[i] && 
                !corellianCancelado[i] && encontrados.get(i).isEmpty()) {
                outbox = new ACLMessage();
                outbox.setSender(getAID());

                outbox.addReceiver(new AID(corellians.get(i), AID.ISLOCALNAME));
                outbox.setPerformative(ACLMessage.CANCEL);
                outbox.setOntology("COMMITMENT");
                outbox.setConversationId(sessionKey); 
                outbox.setContent("CANCEL CREW " + password);
                this.LARVAsend(outbox);

                // Marcar corellian como cancelado
                corellianCancelado[i] = true;
            }
        }
        
        
        /*
        * @author Ahmed
        */
        // Condicion de seguida o parada
        
        // Comprobar que estan todos los agentes cancelados
        boolean todosCancelados = 
                fighterCancelado[0] 
                && fighterCancelado[1] 
                && corellianCancelado[0] 
                && corellianCancelado[1];
        
        if (todosCancelados) {
            // En caso de que hayamos cancelado todos los agentes, 
            // vamos a esperar a que todos esten fuera para 
            // cerar el problema abierto
            
            boolean canceladosExitosamente = false;
            while (!canceladosExitosamente) {
                
                // Actualizar condicion de salida preguntandose si ya no estan registrados
                // OJO: podria dar un bloqueo y saturar al DF
                canceladosExitosamente = 
                    this.DFGetAllProvidersOf("FIGHTER " + password).isEmpty() &&
                    this.DFGetAllProvidersOf("CORELLIAN " + password).isEmpty() &&
                    this.DFGetAllProvidersOf("RAZOR " + password).isEmpty();
                
                Info("Esperando a que salgan todos los agentes");
            }
            
            return Status.CLOSEPROBLEM;
        } else {
            // En otro caso seguimos resolviendo el problema
            return Status.SOLVEPROBLEM;
        }
    }
    
    private void actualizarTipoInform(final String content) {
        
        // Falso hasta que se demuestre lo contrario
        move = capture = false;     
        
        // Buscar en el contenido alguna de las palabras clave
        for (String c: content.split(" ")) {
            if (c.toUpperCase().equals("MOVE")) {
                move = true;
                break;
            } else if (c.toUpperCase().equals("CAPTURE")){
                capture = true;
                break;
            }   
        }
        
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
        
        inbox = LARVAblockingReceive();
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();
        
        // Comprobar que la lectura fuese correcta
        if(inbox.getPerformative() == ACLMessage.REFUSE 
                || inbox.getPerformative() == ACLMessage.FAILURE) {
            return false;
        }
        
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

