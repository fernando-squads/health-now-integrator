package br.com.bancadoingresso.integrator;

import java.io.IOException;

import br.com.bancadoingresso.integrator.view.ApplicationConsole;
import br.com.bancadoingresso.integrator.view.ApplicationSwing;

public class Application {

	public static void main(String[] args) throws IOException {
		if (args != null && args.length > 0 && args[0].equals("console")) {
			ApplicationConsole console = new ApplicationConsole();
			console.start();
		} else {
			ApplicationSwing swing = new ApplicationSwing();
			swing.start();
		}
	}
}
