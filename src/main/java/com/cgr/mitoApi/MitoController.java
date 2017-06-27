package com.cgr.mitoApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.cgr.mitoApi.domain.Parcial;

@RestController
@RequestMapping(value = "/mitoAPI")
@CrossOrigin(origins = "*")
public class MitoController {

	private final static Logger log = LoggerFactory.getLogger(MitoController.class);

	private static final String PRFX_LIGAS = "/ligas";
	private static final String PRFX_LIGA = "/auth/liga/{slug}";
	private static final String PRFX_PARCIAIS = "/parciais/{slug}";
	private static final String PRFX_TIME = "/time/slug/{slug}";
	private static final String PRFX_ATLETAS_PONTUADOS = "/atletas/pontuados";

	@Value("${serviceRootURI}")
	private String serviceRootURI;

	@Value("${GLBToken}")
	private String GLBToken;

	private JSONObject pontuados;

	// busca_ligas: "//api.cartolafc.globo.com/ligas?q=",
	@ResponseBody
	@RequestMapping(method = RequestMethod.GET, value = PRFX_LIGAS)
	public String ligas(@RequestParam(name = "q") String param) {
		RestTemplate restTemplate = new RestTemplate();
		String uriReq = serviceRootURI + PRFX_LIGAS + "?q=" + param;
		log.info("requesting " + uriReq);
		String r = restTemplate.getForObject(uriReq, String.class);
		return r;
	}

	// https://api.cartolafc.globo.com/auth/liga/fornax-champions-league
	@ResponseBody
	@RequestMapping(method = RequestMethod.GET, value = PRFX_LIGA)
	public String liga(@PathVariable(value = "slug") String slug) {
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-GLB-Token", GLBToken);
		HttpEntity<String> entity = new HttpEntity<String>(headers);

		String uriReq = serviceRootURI + PRFX_LIGA.replace("{slug}", slug);

		log.info("requesting " + uriReq);
		String r = null;
		try {
			ResponseEntity<String> exchange = restTemplate.exchange(uriReq, HttpMethod.GET, entity, String.class);
			r = exchange.getBody();

			atualizarParciaisGerais();

			JSONObject json = null;
			try {
				json = new JSONObject(r);
				JSONArray times = json.getJSONArray("times");
				for (int index = 0; index < times.length(); index++) {
					JSONObject time = times.getJSONObject(index);
					JSONObject pontos = time.getJSONObject("pontos");
					Parcial parcial = getParcial(time.getString("slug"));
					pontos.put("parcial", parcial.getTotal());
					pontos.put("atletas", parcial.getTotalAtletas());
					double campeonato = pontos.getDouble("campeonato");
					pontos.put("campparcial", campeonato + parcial.getTotal());
					// log.info(pontos.toString());
				}
				log.info(times.length() + " times retornados");
			} catch (JSONException e) {
				log.error(e.getMessage(), e);
			}
			if (json != null) {
				r = json.toString();
			}
			// log.info(r);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			r = "{\"error\": \"" + e.getMessage() + "\"}";
		}
		return r;
	}

	private void atualizarParciaisGerais() {
		RestTemplate restTemplate = new RestTemplate();
		String uriReq = serviceRootURI + PRFX_ATLETAS_PONTUADOS;
		log.info("requesting " + uriReq);
		String parciais = restTemplate.getForObject(uriReq, String.class);
		if (parciais != null) {
			try {
				JSONObject pontuados = new JSONObject(parciais).getJSONObject("atletas");
				this.pontuados = pontuados;
			} catch (JSONException e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	private Parcial getParcial(String slug) {
		Parcial r = new Parcial();
		log.info("parcial " + slug);
		RestTemplate restTemplate = new RestTemplate();
		String uriReq = serviceRootURI + PRFX_TIME.replace("{slug}", slug);
		log.info("requesting " + uriReq);
		String atletasString = restTemplate.getForObject(uriReq, String.class);
		JSONObject atletas = null;
		try {
			atletas = new JSONObject(atletasString);
			JSONArray atletasArr = atletas.getJSONArray("atletas");
			Double parcial = 0d;
			int totalAtletas = 0;
			for (int index = 0; index < atletasArr.length(); index++) {
				JSONObject atleta = atletasArr.getJSONObject(index);
				Integer atleta_id = atleta.getInt("atleta_id");
				Double pontuacao = getPontuacao(atleta_id);
				if (pontuacao != null) {
					totalAtletas++;
					parcial += pontuacao;
				}
			}
			r.setAtletas(totalAtletas);
			r.setTotal(parcial);
		} catch (JSONException e) {
			// log.error(e.getMessage(), e);
		}
		return r;
	}

	private Double getPontuacao(Integer atleta_id) {
		if (this.pontuados != null) {
			JSONObject pontuado;
			try {
				pontuado = this.pontuados.getJSONObject(String.valueOf(atleta_id));
				JSONObject scout = null;
				try {
					scout = pontuado.getJSONObject("scout");
				} catch (Exception e) {
				}
				double pontuacao = pontuado.getDouble("pontuacao");
				if (pontuado != null && pontuacao != 0d || !scout.toString().equals("{}")) {
					return pontuacao;
				} else {
					// int clubeId = pontuado.getInt("clube_id");
					return null;
				}
			} catch (JSONException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	// api.cartolafc.globo.com/atletas/pontuados
	@ResponseBody
	@RequestMapping(method = RequestMethod.GET, value = PRFX_PARCIAIS)
	public String parciaisTime(@PathVariable(value = "slug") String slug) {

		return null;
	}

	// busca_times: "//api.cartolafc.globo.com/times?q=",
}
