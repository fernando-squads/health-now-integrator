package br.com.bancadoingresso.integrator.api;
import java.io.IOException;
public final class ActivationRequired extends IOException {
    private static final long serialVersionUID = 1L;
    public ActivationRequired() { super("Ativação necessária. Verifique a API e a versão do integrador; solicite um novo código se o token estiver expirado, revogado ou perdido."); }
}
