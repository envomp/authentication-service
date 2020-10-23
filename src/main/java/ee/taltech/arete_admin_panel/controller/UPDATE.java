package ee.taltech.arete_admin_panel.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UPDATE {
	private String gitTestSource;
	private String[] dockerExtra;
}