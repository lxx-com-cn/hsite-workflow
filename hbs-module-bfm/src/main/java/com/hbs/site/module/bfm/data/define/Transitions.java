package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transitions implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlElementWrapper(localName = "Transition", useWrapping = false)
    @JacksonXmlProperty(localName = "Transition")
    private List<Transition> transitions;
}