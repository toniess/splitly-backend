package dev.toniess.splitly.config;

import dev.toniess.splitly.common.Money;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

/**
 * Деньги уходят наружу строкой с двумя знаками: {@code "1234.50"}.
 *
 * <p>Числом их сериализовать нельзя: в JavaScript все числа — double, и значение
 * теряет точность ещё до того, как клиент его увидит. Строка переносится без потерь,
 * а разбирать её клиент будет своим десятичным типом.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer moneyAsStringCustomizer() {
        SimpleModule module = new SimpleModule("money-as-string");
        module.addSerializer(BigDecimal.class, new MoneySerializer());
        return builder -> builder.addModule(module);
    }

    private static class MoneySerializer extends ValueSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(Money.normalize(value).toPlainString());
        }
    }
}
