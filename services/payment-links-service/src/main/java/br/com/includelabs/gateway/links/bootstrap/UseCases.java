package br.com.includelabs.gateway.links.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.links.application.CreateLink;
import br.com.includelabs.gateway.links.domain.port.LinkStore;
@Configuration public class UseCases {@Bean CreateLink createLink(LinkStore store){return new CreateLink(store);}}
