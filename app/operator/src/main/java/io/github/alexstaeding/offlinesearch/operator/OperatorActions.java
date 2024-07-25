package io.github.alexstaeding.offlinesearch.operator;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.github.alexstaeding.offlinesearch.network.NodeId;
import scala.Option;

public class OperatorActions {

  private static final int p2pPort = 9400;
  private static final int contentPort = 80;

  private final KubernetesClient client;

  public OperatorActions(KubernetesClient client) {
    this.client = client;
  }

  boolean createNode(NodeId id, Option<String> visualizerUrl) {

    var appName = "headless-" + id.toHex();

    var deploySpec = new DeploymentBuilder()
      .withNewMetadata()
      .withName("headless-" + id.toHex())
      .withNamespace(client.getNamespace())
      .endMetadata()
      .withNewSpec()
      .withNewSelector()
      .addToMatchLabels("app", appName)
      .endSelector();

    var ctr = deploySpec.withNewTemplate()
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
      .withType("LoadBalancer")
      .addNewPort()
      .withPort(80)
      .withTargetPort(new IntOrString(contentPort))
      .endPort()
      .addNewPort()
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

    return true;
  }

  boolean removeNode(NodeId id) {
    return true;
  }
}
