package br.com.bancadoingresso.integrator.view;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ApplicationConsole {
	public void start() throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Informe a URL do banco de dados [localhost]:");
		String url = br.readLine();
		System.out.println("Informe a PORTA do banco de dados [5432]:");
		String port = br.readLine();
		System.out.println(url + " - " + port);
	}
}
