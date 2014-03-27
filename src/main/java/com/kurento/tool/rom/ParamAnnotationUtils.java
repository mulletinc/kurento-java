package com.kurento.tool.rom;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.kurento.kmf.jsonrpcconnector.Props;
import com.kurento.tool.rom.server.MediaApiException;
import com.kurento.tool.rom.server.Param;

public class ParamAnnotationUtils {

	public static Props extractProps(Annotation[][] annotations, Object[] args)
			throws MediaApiException {

		Props props = null;

		if (args != null && args.length > 0) {

			props = new Props();
			for (int i = 0; i < args.length; i++) {

				Param param = getParamAnnotation(annotations[i]);
				props.add(param.value(), args[i]);
			}
		}

		return props;
	}

	public static List<String> getParamNames(Method method)
			throws MediaApiException {
		return getParamNames(method.getParameterAnnotations());
	}

	public static List<String> getParamNames(Constructor<?> constructor)
			throws MediaApiException {
		return getParamNames(constructor.getParameterAnnotations());
	}

	public static List<String> getParamNames(Annotation[][] annotationsParams)
			throws MediaApiException {

		List<String> paramNames = new ArrayList<>();

		for (int x = 0; x < annotationsParams.length; x++) {
			Annotation[] annotationsParam = annotationsParams[x];
			paramNames.add(getParamAnnotation(annotationsParam).value());
		}

		return paramNames;
	}

	public static Param getParamAnnotation(Annotation[] annotationsParam)
			throws MediaApiException {

		Param param = null;

		for (int j = 0; j < annotationsParam.length; j++) {
			if (annotationsParam[j] instanceof Param) {
				param = (Param) annotationsParam[j];
				break;
			}
		}

		if (param == null) {
			throw new MediaApiException("@Param annotation must be specified"
					+ " in all methods and constructor params");
		}

		return param;
	}

	public static Object[] extractEventParams(
			Annotation[][] parameterAnnotations, Props data)
			throws MediaApiException {

		List<String> names = getParamNames(parameterAnnotations);

		Object[] params = new Object[names.size()];

		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			params[i] = data.getProp(name);
		}

		return params;
	}
}