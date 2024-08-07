package io.smallrye.stork.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.smallrye.stork.api.config.ConfigWithType;
import io.smallrye.stork.api.config.ServiceConfig;
import io.smallrye.stork.spi.config.ConfigProvider;

/**
 * Stork config provider for tests, allows easily configuring stuff programmatically
 */
public class TestConfigProvider implements ConfigProvider {
    private static final List<ServiceConfig> configs = new ArrayList<>();

    private static int priority = Integer.MAX_VALUE - 1;

    public static void setPriority(int priority) {
        TestConfigProvider.priority = priority;
    }

    public static int getPriority() {
        return priority;
    }

    @Deprecated
    public static void addServiceConfig(String name, String loadBalancer, String serviceDiscovery,
            String serviceRegistrar, Map<String, String> loadBalancerParams, Map<String, String> serviceDiscoveryParams,
            boolean secure) {
        if (secure) {
            serviceDiscoveryParams.put("secure", "true");
        }
        addServiceConfig(name, loadBalancer, serviceDiscovery, null, loadBalancerParams, serviceDiscoveryParams, null);
    }

    public static void addServiceConfig(String name, String loadBalancer, String serviceDiscovery,
            String serviceRegistrar, Map<String, String> loadBalancerParams, Map<String, String> serviceDiscoveryParams,
            Map<String, String> serviceRegistrarParams) {
        configs.add(new ServiceConfig() {
            @Override
            public String serviceName() {
                return name;
            }

            @Override
            public ConfigWithType loadBalancer() {
                return loadBalancer == null ? null : new ConfigWithType() {
                    @Override
                    public String type() {
                        return loadBalancer;
                    }

                    @Override
                    public Map<String, String> parameters() {
                        return Objects.requireNonNullElse(loadBalancerParams, Collections.emptyMap());
                    }
                };
            }

            @Override
            public ConfigWithType serviceDiscovery() {
                return serviceDiscovery == null ? null : new ConfigWithType() {
                    @Override
                    public String type() {
                        return serviceDiscovery;
                    }

                    @Override
                    public Map<String, String> parameters() {
                        return Objects.requireNonNullElse(serviceDiscoveryParams, Collections.emptyMap());
                    }
                };
            }

            @Override
            public ConfigWithType serviceRegistrar() {
                return serviceRegistrar == null ? null : new ConfigWithType() {
                    @Override
                    public String type() {
                        return serviceRegistrar;
                    }

                    @Override
                    public Map<String, String> parameters() {
                        return Objects.requireNonNullElse(serviceRegistrarParams, Collections.emptyMap());
                    }
                };
            }
        });
    }

    public static void clear() {
        configs.clear();
    }

    @Override
    public List<ServiceConfig> getConfigs() {
        return configs;
    }

    @Override
    public int priority() {
        return priority;
    }
}
