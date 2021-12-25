
package tiefighter;

import appboot.LARVABoot;
import java.util.concurrent.TimeUnit;


public class Practica3 {
    public static void main(String[] args) throws InterruptedException {
        LARVABoot connection = new LARVABoot();
        
        connection.Boot("isg2.ugr.es", 1099);
        
        connection.launchAgent("106-CORELLIAN-10", Practica3Corellian.class);
        connection.launchAgent("106-CORELLIAN-11", Practica3Corellian.class);        
        
        connection.launchAgent("106-TIEFIGHTER-10", Practica3TieFighter.class);
        connection.launchAgent("106-TIEFIGHTER-11", Practica3TieFighter.class);
        
        connection.launchAgent("106-DESTROYER-7", Practica3Destroyer.class);
        
        connection.WaitToShutDown();
       
    }
    
}
