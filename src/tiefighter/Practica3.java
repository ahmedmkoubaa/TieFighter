
package tiefighter;

import appboot.LARVABoot;
import java.util.concurrent.TimeUnit;


public class Practica3 {
    public static void main(String[] args) throws InterruptedException {
        LARVABoot connection = new LARVABoot();
        
        connection.Boot("isg2.ugr.es", 1099);
        
        connection.launchAgent("106-DESTROYER-14", Practica3Destroyer.class);
        
//        connection.launchAgent("106-RAZOR-3", Practica3Razor.class);
        
        connection.launchAgent("106-CORELLIAN-27", Practica3Corellian.class);
        connection.launchAgent("106-CORELLIAN-28", Practica3Corellian.class);        
        
        connection.launchAgent("106-TIEFIGHTER-27", Practica3TieFighter.class);
        connection.launchAgent("106-TIEFIGHTER-28", Practica3TieFighter.class);
        
        connection.WaitToShutDown();
       
    }
    
}
