/*** Eclipse Class Decompiler plugin, copyright (c) 2012 Chao Chen (cnfree2000@hotmail.com) ***/
package com.ufida.eip.adaptor.servlet;

import com.ufida.eip.nc.context2.ESBContextForNC;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.bs.framework.adaptor.IHttpServletAdaptor;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.core.service.IFwLogin;
import nc.bs.framework.server.ISecurityTokenCallback;
import nc.bs.logging.Logger;
import nc.vo.eip.eis.EISVO;
import nc.vo.pfxx.exception.PfxxException;
import nc.vo.pfxx.util.PfxxUtils;
import nc.vo.pub.BusinessException;
import nc.vo.pub.BusinessRuntimeException;

import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

public class ServletForESBAdaptor implements IHttpServletAdaptor {
	private String dataSource;

	public void doAction(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
//		Map<String,Object> map = new HashMap<String,Object>();
//		byte[] login = NCLocator.getInstance().lookup(IFwLogin.class).login("", "", map);
//		
    ISecurityTokenCallback sc = (ISecurityTokenCallback)NCLocator.getInstance().lookup(ISecurityTokenCallback.class);
    sc.token("NCSystem".getBytes(), "uapesb".getBytes());
    try
    {
      ESBContextForNC ncContextForESB = getESBContextForNC(request);
      initDataSource(ncContextForESB.getAccount());

      Logger.info("NC业务处理...");
      Object esbService = NCLocator.getInstance().lookup(ncContextForESB.getEsbServiceClassName());

      Object[] args = ncContextForESB.getEsbServiceMethodArgs();
      if (args == null) {
        args = new Object[0];
      }
      int arguments = args.length;
      Class[] parameterTypes = new Class[arguments];
      Class[] esbServiceMethodArgTypes = ncContextForESB.getEsbServiceMethodArgTypes();

      if ((esbServiceMethodArgTypes == null) || 
        (esbServiceMethodArgTypes.length != arguments));
      try {
        throw new BusinessException("调用服务时传入的参数个数与传入的参数类型个数不一致，请确认!");
      }
      catch (BusinessException e)
      {
        e.printStackTrace();

        parameterTypes = esbServiceMethodArgTypes; break label186:

        for (int i = 0; i < arguments; ++i) {
          if (args[i] == null)
            parameterTypes[i] = Object.class;
          else {
            parameterTypes[i] = args[i].getClass();
          }

        }

        label186: Object invokeRes = null;
        try {
          invokeRes = MethodUtils.invokeMethod(esbService, ncContextForESB.getEsbServiceMethodName(), args, parameterTypes);

          Logger.info("NC业务处理结束...");
          writeResult(response, invokeRes);
        } catch (InvocationTargetException e) {
          BaseDAO baseDAO = new BaseDAO();
          try {
            String sql = String.format("update %s set %s='%s' where %s='%s'", new Object[] { EISVO.getDefaultTableName(), "msgstatus", Integer.valueOf(-1), "code", ncContextForESB.getMessageId() });

            baseDAO.executeUpdate(sql);
          }
          catch (DAOException e1)
          {
          }
          throw new BusinessException("调用NC服务出现异常：" + e); }
      }
    } catch (Exception e) {
      Logger.error("UAPESB调用NC服务出现异常!");
      response.setCharacterEncoding("UTF-8");
      response.setStatus(417);
      response.getWriter().write(e.getMessage());
    }
  }

	private void writeResult(HttpServletResponse response, Object invokeRes)
			throws IOException {
		ObjectOutputStream oos = null;
		try {
			OutputStream outstr = response.getOutputStream();
			oos = new ObjectOutputStream(outstr);

			oos.writeObject(invokeRes);
		} finally {
			IOUtils.closeQuietly(oos);
		}
	}

	private ESBContextForNC getESBContextForNC(HttpServletRequest request)
			throws IOException, ClassNotFoundException {
		ObjectInputStream ois = null;
		try {
			ServletInputStream inputStream = request.getInputStream();
			ois = new ObjectInputStream(inputStream);
			ESBContextForNC ncContextForESB = (ESBContextForNC) ois
					.readObject();

			ESBContextForNC localESBContextForNC1 = ncContextForESB;

			return localESBContextForNC1;
		} finally {
			IOUtils.closeQuietly(ois);
		}
	}

	private void initDataSource(String accountCode) {
		if (StringUtils.isEmpty(accountCode)) {
			return;
		}

		try {
			this.dataSource = PfxxUtils.getDataSourceByAccountCode(accountCode);
		} catch (PfxxException e) {
			throw new BusinessRuntimeException(e.getMessage());
		}

		InvocationInfoProxy.getInstance().setUserDataSource(this.dataSource);
	}
}