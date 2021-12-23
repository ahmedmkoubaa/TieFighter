
package tiefighter;

import appboot.LARVABoot;


public class Practica3 {
    public static void main(String[] args) {
        LARVABoot connection = new LARVABoot();
        
        connection.Boot("isg2.ugr.es", 1099);
        connection.launchAgent("106-TIEFIGHTER-2", Practica3TieFighter.class);
        connection.launchAgent("106-CORELLIAN-2", Practica3Corellian.class);
        connection.launchAgent("106-DESTROYER-2", Practica3Destroyer.class);
        connection.WaitToShutDown();
       
    }
    
}
