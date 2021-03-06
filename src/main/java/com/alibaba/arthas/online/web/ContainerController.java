package com.alibaba.arthas.online.web;

import java.util.List;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.alibaba.arthas.online.DockerService;
import com.alibaba.arthas.online.web.Result.ResultBuilder;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableMap;

@Controller
@RequestMapping("container")
public class ContainerController {

	private static final Logger logger = LoggerFactory.getLogger(ContainerController.class);

	@Autowired
	DockerService dockerService;

	@RequestMapping("/get")
	@ResponseBody
	public ResponseEntity<Result> getContainer(@SessionAttribute(required = false) String containerId,
			HttpSession session) throws DockerException, InterruptedException {
		String id = session.getId();

		ResultBuilder resultBuilder = Result.builder();

		if (StringUtils.isEmpty(containerId)) {
			resultBuilder.withError("exist", false);
			return resultBuilder.fail().buildRestResult();
		}

		ContainerInfo containerInfo = dockerService.queryConatiner(containerId);

		Boolean running = containerInfo.state().running();

		resultBuilder.withResult("exist", running);

		if(Boolean.TRUE.equals(running)) {
			ImmutableMap<String, List<PortBinding>> ports = containerInfo.networkSettings().ports();

			List<PortBinding> bindings = ports.get("8563/tcp");

			PortBinding portBinding = bindings.get(0);

			resultBuilder.withResult("port", portBinding.hostPort())
			.withResult("containerId", containerId);
		}

		return resultBuilder.buildRestResult();
	}

	@RequestMapping("/getOrStart")
	@ResponseBody
	public ResponseEntity<Result> getOrStartContainer(@SessionAttribute(required = false) String containerId,
			HttpSession session) throws DockerException, InterruptedException {
		Result result = null;

		if (StringUtils.isNotEmpty(containerId)) {
			ContainerInfo containerInfo = dockerService.queryConatiner(containerId);
			Boolean running = containerInfo.state().running();

			if(Boolean.TRUE.equals(running)) {
				ResultBuilder resultBuilder = Result.builder();

				ImmutableMap<String, List<PortBinding>> ports = containerInfo.networkSettings().ports();

				List<PortBinding> bindings = ports.get("8563/tcp");

				PortBinding portBinding = bindings.get(0);

				resultBuilder.withResult("port", portBinding.hostPort())
				.withResult("containerId", containerId);

				result = resultBuilder.build();
			}
		}

		if(result == null) {
			result = dockerService.startContainer();
			session.setAttribute("containerId", result.getResult().get("containerId"));
		}

		return result.toResponseEntity();
	}

	@RequestMapping("/start")
	@ResponseBody
	public ResponseEntity<Result> startContainer(@SessionAttribute(required = false) String containerId,
			HttpSession session) throws DockerException, InterruptedException {

		if (containerId != null) {
			try {
				dockerService.stopContainer(containerId);
			} catch (Throwable t) {
				logger.error("stop docker container error, id: {}", containerId, t);
			}
		}

		Result result = dockerService.startContainer();

		session.setAttribute("containerId", result.getResult().get("id"));

		return result.toResponseEntity();
	}

	@RequestMapping("/stop")
	@ResponseBody
	public ResponseEntity<Result> stopContainer(@SessionAttribute(required = false) String containerId,
			HttpSession session) throws DockerException, InterruptedException {
		dockerService.stopContainer(containerId);
		return Result.success().withResult("containerId", containerId).buildRestResult();
	}

}
