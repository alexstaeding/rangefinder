package io.github.alexstaeding.offlinesearch.operator;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.github.alexstaeding.offlinesearch.network.NodeId;

public class OperatorActions {

  private final KubernetesClient client;

  public OperatorActions(KubernetesClient client) {
    this.client = client;
  }

  boolean createNode(NodeId id) {
    var pod = new PodBuilder()
      .withNewMetadata()
      .withName("headless-" + id.toHex())
      .endMetadata()
      .withNewSpec()
      .addNewContainer()
      .withName("headless")
      .withImage("offline-search-headless:latest")
      .withImagePullPolicy("Never")
      .addNewEnv()
      .withName("NODE_ID")
      .withValue(id.toHex())
      .endEnv()
      .endContainer()
      .endSpec()
      .build();

    client.pods().inNamespace(client.getNamespace()).resource(pod).createOr(NonDeletingOperation::update);
    return true;
  }

  boolean removeNode(NodeId id) {
    return true;
  }
}
