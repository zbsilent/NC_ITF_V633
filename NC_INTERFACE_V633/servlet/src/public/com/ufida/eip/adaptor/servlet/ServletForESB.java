/*** Eclipse Class Decompiler plugin, copyright (c) 2012 Chao Chen (cnfree2000@hotmail.com) ***/
package com.ufida.eip.adaptor.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.ufida.eip.adaptor.context.NCRetObj;
import com.ufida.eip.adaptor.exception.NCAdaptorRuntimeException;
import com.ufida.eip.adaptor.json.JSonParserUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.bs.framework.adaptor.IHttpServletAdaptor;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.server.ISecurityTokenCallback;
import nc.bs.logging.Logger;
import nc.bs.uap.sfapp.util.SFAppServiceUtil;
import nc.itf.uap.sf.IConfigFileService;
import nc.vo.eip.eis.EISVO;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.BusinessRuntimeException;
import nc.vo.pub.CircularlyAccessibleValueObject;
import nc.vo.pub.ExtendedAggregatedValueObject;
import nc.vo.pub.SuperVO;
import nc.vo.sm.config.Account;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

public class ServletForESB implements IHttpServletAdaptor {
	private static final String ACCOUNT_CODE = "accountCode";
	private static final String MESSAGE_ID = "messageId";
	private static final String SERVICE_INFO = "serviceInfo";
	private static final String SERVICE_CLASS_NAME = "serviceClassName";
	private static final String SERVICE_METHOD_NAME = "serviceMethodName";
	private static final String SERVICE_METHOD_ARG_INFO = "serviceMethodArgInfo";
	private static final String HEAD = "head";
	private static final String BODY = "body";
	private static final String BODYS = "bodys";
	private static final String AGG = "agg";
	private static final String ARG_VALUE = "argValue";
	private static final String ARG_TYPE = "argType";
	private String dataSource;
	private Gson gson;

	public void doAction(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		try {
			ISecurityTokenCallback sc = (ISecurityTokenCallback) NCLocator
					.getInstance().lookup(ISecurityTokenCallback.class);
			sc.token("NCSystem".getBytes(), "uapesb".getBytes());

			this.gson = JSonParserUtils.createGson();

			byte[] byteArray = IOUtils.toByteArray(request.getInputStream());
			if (byteArray == null) {
				throw new NCAdaptorRuntimeException("未设置调用信息，请重新设置!");
			}

			InputStreamReader inStreamReader = new InputStreamReader(
					new ByteArrayInputStream(byteArray), "utf-8");

			JsonParser jsonParser = new JsonParser();
			JsonElement ncServiceCallInfo = jsonParser.parse(inStreamReader);

			Object retObj = null;
			if (ncServiceCallInfo != null) {
				retObj = callNCService(ncServiceCallInfo.getAsJsonObject());
			}
			if (retObj != null) {
				String retJson = this.gson.toJson(retObj);
				response.setContentType("application/json; charset='utf-8'");
				response.setCharacterEncoding("utf-8");
				response.getWriter().write(retJson);
			}
		} catch (Exception e) {
			Logger.error("UAPESB调用NC服务出现异常!");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(417);
			response.getWriter().write(e.getMessage());
		}
	}

	public Object callNCService(JsonObject jsonObj) throws Exception {
		Logger.info("NC业务处理开始...");

		JsonPrimitive accountCode = jsonObj.getAsJsonPrimitive("accountCode");
		if (accountCode != null) {
			initDataSource(accountCode.getAsString());
		}

		JsonObject serviceInfo = jsonObj.getAsJsonObject("serviceInfo");

		String serviceClassName = serviceInfo.getAsJsonPrimitive(
				"serviceClassName").getAsString();

		String methodName = serviceInfo.getAsJsonPrimitive("serviceMethodName")
				.getAsString();

		JsonArray jsonArgInfoArray = serviceInfo
				.getAsJsonArray("serviceMethodArgInfo");

		Object[] argValues = null;
		Class[] argTypes = null;
		int argCount = 0;
		int argIndex;
		if (jsonArgInfoArray != null) {
			argCount = jsonArgInfoArray.size();
			if (argCount > 0) {
				argValues = new Object[argCount];
				argTypes = new Class[argCount];
			}

			argIndex = 0;
			for (Object jsonArgInfo : jsonArgInfoArray) {
				JsonObject jsonArgInfoObj = (JsonObject) jsonArgInfo;

				JsonObject jsonArgTypeObj = jsonArgInfoObj
						.getAsJsonObject("argType");
				JsonObject jsonArgValueObj = jsonArgInfoObj
						.getAsJsonObject("argValue");

				Boolean isAgg = Boolean.valueOf(jsonArgInfoObj
						.getAsJsonPrimitive("agg").getAsBoolean());

				if (isAgg.booleanValue()) {
					JsonPrimitive jsonArgTypeBody = jsonArgTypeObj
							.getAsJsonPrimitive("agg");
					if (jsonArgTypeBody != null) {
						String argTypeClassName = jsonArgTypeBody.getAsString();
						if (StringUtils.isEmpty(argTypeClassName)) {
							throw new NCAdaptorRuntimeException(
									"聚合VO类名为空，请设置聚合VO类名!");
						}
						argTypes[argIndex] = Class.forName(argTypeClassName);

						if (argTypes[argIndex] != null)
							argValues[argIndex] = parseAggVO(jsonArgTypeObj,
									jsonArgValueObj, argTypes[argIndex]);
					}
				} else {
					JsonPrimitive jsonArgTypeBody = jsonArgTypeObj
							.getAsJsonPrimitive("body");
					if (jsonArgTypeBody != null) {
						String argTypeClassName = jsonArgTypeBody.getAsString();
						if (StringUtils.isEmpty(argTypeClassName)) {
							throw new NCAdaptorRuntimeException(
									"参数类型设置的类名为空，请设置参数类型类名!");
						}
						argTypes[argIndex] = Class.forName(argTypeClassName);

						if (argTypes[argIndex] != null) {
							JsonElement jsonElement = jsonArgValueObj
									.get("body");
							if (jsonElement instanceof JsonObject) {
								argValues[argIndex] = JSonParserUtils
										.parseJSonToPOJO(
												(JsonObject) jsonElement,
												argTypes[argIndex]);
							} else if (jsonElement instanceof JsonArray) {
								JsonArray jsonArray = (JsonArray) jsonElement;
								List jsonArrayList = new ArrayList();
								for (JsonElement jsonEle : jsonArray) {
									if (jsonEle instanceof JsonPrimitive) {
										jsonArrayList.add(ConvertUtils.convert(
												((JsonPrimitive) jsonEle)
														.getAsString(),
												argTypes[argIndex]));
									} else if (jsonEle instanceof JsonObject) {
										jsonArrayList.add(JSonParserUtils
												.parseJSonToPOJO(
														(JsonObject) jsonEle,
														argTypes[argIndex]));
									}
								}

								argValues[argIndex] = jsonArrayList;
							} else if (jsonElement instanceof JsonPrimitive) {
								String fieldStr = jsonElement.getAsString();
								if (!(StringUtils.isEmpty(fieldStr))) {
									argValues[argIndex] = ConvertUtils.convert(
											fieldStr, argTypes[argIndex]);
								}
							}
						}
					}
				}

				++argIndex;
			}
		}

		Object ncService = NCLocator.getInstance().lookup(serviceClassName);
		Logger.debug("服务名称:" + serviceClassName + ";服务方法:" + methodName);

		Object invokeRes = null;
		try {
			try {
				invokeRes = MethodUtils.invokeMethod(ncService, methodName,
						argValues, argTypes);
			} catch (NoSuchMethodException e) {
				Method[] methods = ncService.getClass().getMethods();
				int i = 0;
				for (int size = methods.length; i < size; ++i) {
					BaseDAO baseDAO;
					StringWriter sw;
					Object retPojo;
					if (methods[i].getName().equals(methodName)) {
						Class[] methodsParams = methods[i].getParameterTypes();
						int methodParamSize = methodsParams.length;
						if (methodParamSize == argCount) {
							Method method = MethodUtils
									.getAccessibleMethod(methods[i]);
							invokeRes = method.invoke(ncService, argValues);
							break;
						}
					}
				}
			}
		} catch (InvocationTargetException e) {
			baseDAO = new BaseDAO();
			try {
				String sql = String.format(
						"update %s set %s='%s' where %s='%s'", new Object[] {
								EISVO.getDefaultTableName(),
								"msgstatus",
								Integer.valueOf(-1),
								"code",
								jsonObj.getAsJsonPrimitive("messageId")
										.getAsString() });

				baseDAO.executeUpdate(sql);
			} catch (DAOException e1) {
			}
			sw = new StringWriter();
			e.getTargetException().printStackTrace(new PrintWriter(sw));
			throw new BusinessException("调用NC服务出现异常：" + sw.toString());
		}
		Logger.info("NC业务处理结束...");

		retPojo = convertNCVOToPojo(invokeRes);
		return retPojo;
	}

	private Object parseAggVO(JsonObject jsonArgTypeObj,
			JsonObject jsonArgValueObj, Class aggVoClass) throws Exception {
		Object aggvo = aggVoClass.newInstance();

		JsonPrimitive aggHeadObj = jsonArgTypeObj.getAsJsonPrimitive("head");
		Object headvo = null;
		if (aggHeadObj != null) {
			String headClassName = aggHeadObj.getAsString();
			if (StringUtils.isEmpty(headClassName)) {
				throw new BusinessException("未定义聚合VO表头类名，请确认!");
			}
			headvo = JSonParserUtils.parseJSonToPOJO(
					(JsonObject) jsonArgValueObj.get("head"),
					Class.forName(headClassName));
		}

		JsonArray jsonBodysArgTypeArray = jsonArgTypeObj
				.getAsJsonArray("bodys");
		JsonObject jsonBodysArgValueObj = jsonArgValueObj
				.getAsJsonObject("bodys");

		Map bodyvosMap = new HashMap();
		if (jsonBodysArgTypeArray != null) {
			for (Object bodyClassName : jsonBodysArgTypeArray) {
				JsonArray jsonBodysArgValueArray = jsonBodysArgValueObj
						.getAsJsonArray(((JsonPrimitive) bodyClassName)
								.getAsString());

				if (jsonBodysArgValueArray != null) {
					List bodyvos = new ArrayList();
					Class childrenClass = Class
							.forName(((JsonPrimitive) bodyClassName)
									.getAsString());
					for (Object childrenValue : jsonBodysArgValueArray) {
						Object javaObject = JSonParserUtils.parseJSonToPOJO(
								(JsonObject) childrenValue, childrenClass);
						bodyvos.add(javaObject);
					}
					Object childrenObj = childrenClass.newInstance();
					if (childrenObj instanceof SuperVO) {
						bodyvosMap
								.put(((SuperVO) childrenObj).getTableName(),
										bodyvos.toArray(new CircularlyAccessibleValueObject[0]));
					}
				}
			}
		}

		if (aggvo instanceof AggregatedValueObject) {
			((AggregatedValueObject) aggvo)
					.setParentVO((CircularlyAccessibleValueObject) headvo);
			Set bodyKeySet = bodyvosMap.keySet();
			for (String bodyKey : bodyKeySet) {
				CircularlyAccessibleValueObject[] bodyValueObjs = (CircularlyAccessibleValueObject[]) bodyvosMap
						.get(bodyKey);
				if ((bodyValueObjs != null) && (bodyValueObjs.length > 0)) {
					Object newBodyObjs = Array.newInstance(
							bodyValueObjs[0].getClass(), bodyValueObjs.length);
					System.arraycopy(bodyValueObjs, 0, newBodyObjs, 0,
							bodyValueObjs.length);
					((AggregatedValueObject) aggvo)
							.setChildrenVO((CircularlyAccessibleValueObject[]) (CircularlyAccessibleValueObject[]) newBodyObjs);
				}
			}
		} else if (aggvo instanceof ExtendedAggregatedValueObject) {
			((ExtendedAggregatedValueObject) aggvo)
					.setParentVO((CircularlyAccessibleValueObject) headvo);
			String[] tables = ((ExtendedAggregatedValueObject) aggvo)
					.getTableCodes();
			for (String childTable : tables) {
				((ExtendedAggregatedValueObject) aggvo).setTableVO(childTable,
						(CircularlyAccessibleValueObject[]) bodyvosMap
								.get(childTable));
			}
		}
		return aggvo;
	}

	private Object convertNCVOToPojo(Object ncvo) {
		if (ncvo instanceof AggregatedValueObject) {
			CircularlyAccessibleValueObject headvo = ((AggregatedValueObject) ncvo)
					.getParentVO();

			Map bodysMap = new HashMap();

			CircularlyAccessibleValueObject[] bodyvos = ((AggregatedValueObject) ncvo)
					.getChildrenVO();
			if ((bodyvos != null) && (bodyvos.length > 0)) {
				bodysMap.put(bodyvos[0].getEntityName(), Arrays.asList(bodyvos));
			}

			NCRetObj ncRetObj = new NCRetObj();
			ncRetObj.setAgg(true);
			ncRetObj.setHead(headvo);
			ncRetObj.setBodys(bodysMap);
			return ncRetObj;
		}
		if (ncvo instanceof ExtendedAggregatedValueObject) {
			CircularlyAccessibleValueObject headvo = ((ExtendedAggregatedValueObject) ncvo)
					.getParentVO();

			Map bodysMap = new HashMap();

			String[] tables = ((ExtendedAggregatedValueObject) ncvo)
					.getTableCodes();
			for (String childTable : tables) {
				CircularlyAccessibleValueObject[] bodyvos = ((ExtendedAggregatedValueObject) ncvo)
						.getTableVO(childTable);

				if ((bodyvos != null) && (bodyvos.length > 0)) {
					bodysMap.put(childTable, Arrays.asList(bodyvos));
				}
			}

			NCRetObj ncRetObj = new NCRetObj();
			ncRetObj.setAgg(true);
			ncRetObj.setHead(headvo);
			ncRetObj.setBodys(bodysMap);
			return ncRetObj;
		}
		NCRetObj ncRetObj = new NCRetObj();
		ncRetObj.setAgg(false);
		ncRetObj.setRetObj(ncvo);
		return ncRetObj;
	}

	private void initDataSource(String accountCode) {
		Logger.debug("帐套名称:" + accountCode);

		if (StringUtils.isEmpty(accountCode)) {
			return;
		}

		this.dataSource = getDataSourceByAccountCode(accountCode);
		InvocationInfoProxy.getInstance().setUserDataSource(this.dataSource);
	}

	private String getDataSourceByAccountCode(String accountCode) {
		Logger.info("根据账套编码获得数据源");
		IConfigFileService accountService = SFAppServiceUtil
				.getConfigFileService();
		Account accountObj = null;
		try {
			accountObj = accountService.getAccountByCode(accountCode);
		} catch (BusinessException e) {
			Logger.error(e.getMessage(), e);
		}
		String dataSource = null;
		if (accountObj != null) {
			dataSource = accountObj.getDataSourceName();
		}
		if (dataSource == null) {
			Logger.warn("无法获得数据源,帐套编码:" + accountCode);
			throw new BusinessRuntimeException("无法获得数据源,帐套:" + accountCode);
		}
		return dataSource;
	}

	private byte[] convertObjToByte(Object obj) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(obj);
		} finally {
			IOUtils.closeQuietly(oos);
		}
		return baos.toByteArray();
	}
}