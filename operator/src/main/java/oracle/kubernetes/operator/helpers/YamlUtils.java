// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import java.util.Map;
import java.util.TreeMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

public class YamlUtils {

  public static Yaml newYaml() {
    // always make a new yaml object since it appears to be stateful
    // so there are problems if you try to use the same one to
    // parse different yamls at the same time
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    return new Yaml(new MyConstructor(), new MyRepresenter(), options);
  }

  // We want to be able to test that yamls are identical by doing string compares
  // over the entire yaml.  This doesn't work out of the box since the order of
  // mapping properties is not specified.  So, sort them ...
  //
  // Some kubernetes client classes aren't snakeyaml dump friendly.
  // This class works around these issues too.
  private static class MyRepresenter extends Representer {

    private MyRepresenter() {
      super();
      representers.put(IntOrString.class, new RepresentIntOrString());
    }

    @Override
    protected Node representMapping(Tag tag, Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {
      Map<?, ?> sortedMapping = new TreeMap<>(mapping);
      return super.representMapping(tag, sortedMapping, flowStyle);
    }

    private class RepresentIntOrString implements Represent {
      public Node representData(Object data) {
        IntOrString val = (IntOrString) data;
        if (val.isInteger()) {
          return representScalar(Tag.INT, "" + val.getIntValue(), null);
        } else {
          return representScalar(Tag.STR, val.getStrValue(), null);
        }
      }
    }
  }

  // Some kubernetes client classes aren't snakeyaml load friendly.
  // This class works around these issues.
  private static class MyConstructor extends Constructor {
    private MyConstructor() {
      super();
      yamlClassConstructors.put(NodeId.scalar, new WorkAroundConstructScalar());
    }

    private class WorkAroundConstructScalar extends Constructor.ConstructScalar {
      public Object construct(Node node) {
        Class<?> type = node.getType();
        if (IntOrString.class.equals(type)) {
          ScalarNode sn = (ScalarNode) node;
          Tag tag = sn.getTag();
          String value = sn.getValue();
          if (Tag.STR.equals(tag)) {
            return new IntOrString(value);
          } else if (Tag.INT.equals(tag)) {
            return new IntOrString(Integer.parseInt(value));
          }
        } else if (Quantity.class.equals(type)) {
          ScalarNode sn = (ScalarNode) node;
          return Quantity.fromString(sn.getValue());
        }
        return super.construct(node);
      }
    }
  }
}
