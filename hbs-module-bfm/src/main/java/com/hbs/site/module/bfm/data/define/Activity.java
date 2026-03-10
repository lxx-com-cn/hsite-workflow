package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartEvent.class, name = "START_EVENT"),
        @JsonSubTypes.Type(value = EndEvent.class, name = "END_EVENT"),
        @JsonSubTypes.Type(value = AutoTask.class, name = "AUTO_TASK"),
        @JsonSubTypes.Type(value = UserTask.class, name = "USER_TASK"),
        @JsonSubTypes.Type(value = SubProcess.class, name = "SUB_PROCESS"),
        @JsonSubTypes.Type(value = Gateway.class, name = "EXCLUSIVE_GATEWAY"),
        @JsonSubTypes.Type(value = Gateway.class, name = "PARALLEL_GATEWAY"),
        @JsonSubTypes.Type(value = Gateway.class, name = "INCLUSIVE_GATEWAY"),
        @JsonSubTypes.Type(value = Gateway.class, name = "COMPLEX_GATEWAY")
})
public abstract class Activity implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String description;

    @JacksonXmlProperty(isAttribute = true)
    private String documentation;

    @JacksonXmlProperty(isAttribute = true)
    private String type; // START_EVENT, AUTO_TASK等

    @JacksonXmlProperty(isAttribute = true)
    private Integer x;

    @JacksonXmlProperty(isAttribute = true)
    private Integer y;

    @JacksonXmlProperty(isAttribute = true)
    private Integer width = 120;

    @JacksonXmlProperty(isAttribute = true)
    private Integer height = 60;
}