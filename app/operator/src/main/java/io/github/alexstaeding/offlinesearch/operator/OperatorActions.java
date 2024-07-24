package io.github.alexstaeding.offlinesearch.operator;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.github.alexstaeding.offlinesearch.network.NodeId;
import scala.Option;

public class OperatorActions {

  private final KubernetesClient client;

  public OperatorActions(KubernetesClient client) {
    this.client = client;
  }

  boolean createNode(NodeId id, Option<String> visualizerUrl) {
    var podSpec = new PodBuilder()
      .withNewMetadata()
      .withName("headless-" + id.toHex())
      .endMetadata()
      .withNewSpec();

    var ctr = podSpec.addNewContainer()
      .withName("headless")
      .withImage("offline-search-headless:latest")
      .withImagePullPolicy("Never");

    ctr.addNewEnv()
      .withName("NODE_ID")
      .withValue(id.toHex())
      .endEnv();

    if (visualizerUrl.isDefined()) {
      ctr.addNewEnv()
        .withName("VISUALIZER_URL")
        .withValue(visualizerUrl.get())
        .endEnv();
    }

    ctr.endContainer();

    var pod = podSpec.endSpec()
      .build();

    client.pods().inNamespace(client.getNamespace()).resource(pod).createOr(NonDeletingOperation::update);
    return true;
  }

  boolean removeNode(NodeId id) {
    return true;
  }
}
