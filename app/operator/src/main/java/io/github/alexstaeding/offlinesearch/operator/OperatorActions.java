package io.github.alexstaeding.offlinesearch.operator;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.github.alexstaeding.offlinesearch.network.NodeId;
import org.apache.logging.log4j.Logger;
import scala.Option;

public class OperatorActions {

  private static final int p2pPort = 9400;
  private static final int contentPort = 80;

  private final KubernetesClient client;
  private final Logger logger;

  public OperatorActions(KubernetesClient client, Logger logger) {
    this.client = client;
    this.logger = logger;
  }

  boolean createNode(NodeId id, Option<String> visualizerUrl) {

    var appName = "headless-" + id.toHex();

    var deploySpec = new DeploymentBuilder()
      .withNewMetadata()
      .withName("headless-" + id.toHex())
      .withNamespace(client.getNamespace())
      .addToLabels("app", appName)
      .endMetadata()
      .withNewSpec()
      .withNewSelector()
      .addToMatchLabels("app", appName)
      .endSelector();

    var ctr = deploySpec.withNewTemplate()
      .withNewMetadata()
      .addToLabels("app", appName)
      .endMetadata()
      .withNewSpec()
      .addNewContainer()
      .withName("headless")
      .withImage("images.sourcegrade.org/offline-search/headless:latest");

    ctr.addNewEnv()
      .withName("NODE_ID")
      .withValue(id.toHex())
      .endEnv();

    ctr.addNewEnv()
      .withName("P2P_PORT")
      .withValue(String.valueOf(p2pPort))
      .endEnv();

    ctr.addNewEnv()
      .withName("CONTENT_PORT")
      .withValue(String.valueOf(contentPort))
      .endEnv();

    if (visualizerUrl.isDefined()) {
      ctr.addNewEnv()
        .withName("VISUALIZER_URL")
        .withValue(visualizerUrl.get())
        .endEnv();
    }

    var deploy = ctr.endContainer().endSpec().endTemplate().endSpec().build();

    client.apps()
      .deployments()
      .inNamespace(client.getNamespace())
      .resource(deploy)
      .createOr(NonDeletingOperation::update);

    var serviceSpec = new ServiceBuilder()
      .withNewMetadata()
      .withName(appName)
      .withNamespace(client.getNamespace())
      .addToAnnotations("metallb.universe.tf/address-pool", "address-pool")
      .endMetadata()
      .withNewSpec()
      .addToSelector("app", appName)
      .withType("ClusterIP")
      .addNewPort()
      .withName("content")
      .withPort(80)
      .withTargetPort(new IntOrString(contentPort))
      .endPort()
      .addNewPort()
      .withName("p2p")
      .withPort(9400)
      .withTargetPort(new IntOrString(p2pPort))
      .endPort()
      .endSpec()
      .build();

    client
      .services()
      .inNamespace(client.getNamespace())
      .resource(serviceSpec)
      .createOr(NonDeletingOperation::update);

    addIngressRule(appName, id);

    return true;
  }

  private void addIngressRule(String appName, NodeId id) {
    var ingress = client.network().v1().ingresses().inNamespace(client.getNamespace()).withName("web-ingress");
    if (ingress.get() == null) {
      logger.error("Ingress with name 'web-ingress' not found");
      return;
    }

    HTTPIngressPath newPath = new HTTPIngressPathBuilder()
      .withPath("/" + id.toHex() + "/")
      .withPathType("Prefix")
      .withNewBackend()
      .withNewService()
      .withName(appName)
      .withNewPort()
      .withNumber(contentPort)
      .endPort()
      .endService()
      .endBackend()
      .build();

    ingress.edit(i -> new IngressBuilder(i).editSpec().editFirstRule().editHttp().addToPaths(newPath).endHttp().endRule().endSpec().build());
    logger.info("Added ingress rule for node {}", id.toHex());
  }

  boolean removeNode(NodeId id) {
    return true;
  }
}
