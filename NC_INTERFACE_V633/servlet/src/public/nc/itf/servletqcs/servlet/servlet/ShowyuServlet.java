/**
 * 
 */
package nc.itf.servletqcs.servlet.servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import com.ufida.eip.adaptor.exception.NCAdaptorRuntimeException;

import nc.bs.framework.adaptor.IHttpServletAdaptor;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.server.ISecurityTokenCallback;
import nc.bs.logging.Logger;
import nc.itf.qc.servlets.approve.QcBillApproveItf;
import nc.vo.pub.BusinessException;

/**
 * @author zbsilent
 * 
 */
public class ShowyuServlet implements IHttpServletAdaptor {

	/*
	 * （非 Javadoc）
	 * 
	 * @see
	 * nc.bs.framework.adaptor.IHttpServletAdaptor#doAction(javax.servlet.http
	 * .HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void doAction(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		try {
			// TODO 自动生成的方法存根
			ISecurityTokenCallback sc = (ISecurityTokenCallback) NCLocator
					.getInstance().lookup(ISecurityTokenCallback.class);
			sc.token("NCSystem".getBytes(), "servletqcs".getBytes());

			byte[] byteArray = IOUtils.toByteArray(request.getInputStream());
			if (byteArray == null) {
				throw new NCAdaptorRuntimeException("未设置调用信息，请重新设置!");
			}
			request.setCharacterEncoding("utf-8");
			//出入后数据处理
			Map<Integer, Object> ms = getReqMessage(request, response);
			callNCService(ms, response);
			
		} catch (Exception e) {
			Logger.error("Servlet调用NC服务出现异常!");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(907);
			response.getWriter().write(e.getMessage());
		}

	}

	public void callNCService(Map<Integer,Object> ms,HttpServletResponse response) throws BusinessException, IOException {
		Logger.info("NC业务处理开始...");
		try {
			QcBillApproveItf itf = (QcBillApproveItf)NCLocator.getInstance().lookup(QcBillApproveItf.class);
			Logger.debug("服务启用中.....");
			String message = itf.billApprove(ms.get(1).toString(), ms.get(2).toString(), ms.get(1)==null?null:ms.get(1).toString());
			response.setHeader("Content-type", "text/html;charset=UTF-8"); 
		    ServletOutputStream output = response.getOutputStream();
		    output.write(message.getBytes("UTF-8"));
		    output.flush();
		    output.close();
		} catch (Exception e) {
			Logger.error("Servlet调用NC服务出现异常!");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(907);
			response.getWriter().write(e.getMessage());
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}
		
	}
	
	public Map<Integer,Object> getReqMessage(HttpServletRequest request,HttpServletResponse response) throws BusinessException, IOException{
		String message ="";
		try{
			
			Map<Integer,Object> rs = new HashMap<Integer,Object>();
			String primaryKey = request.getParameter("primarykey") == null ? "": request.getParameter("primarykey").toString();
			String checkok = request.getParameter("checkok") == null ? "": request.getParameter("checkok").toString();
			String para = request.getParameter("para") == null ? "" : request.getParameter("para").toString();
			
			if (!checkok.isEmpty()) {
				if ("1".equals(checkok)) {
					checkok = "合格";
				} else if ("2".equals(checkok)) {
					checkok = "合格品";
				} else if ("3".equals(checkok)) {
					checkok = "优等品";
				} else if ("4".equals(checkok)) {
					checkok = "一等品";
				} else if ("5".equals(checkok)) {
					checkok = "不合格";
				} else {
					message = "质量等级有误";
					throw new BusinessException("质量等级有误");
				}
			} else {
				message = "质质量等级不能为空";
				throw new BusinessException("质量等级不能为空");
			}
			if (primaryKey.isEmpty()) {
				message = "报检单唯一ID不可以为空";
				throw new BusinessException("报检单唯一ID不可以为空");
			}
			rs.put(1, primaryKey);
			rs.put(2, checkok);
			rs.put(3, para);
			return rs;
		}catch(Exception e){
			Logger.error("Servlet调用NC服务出现异常!");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(907);
			response.getWriter().write(message);
		}
		return null;

		
	}
}
