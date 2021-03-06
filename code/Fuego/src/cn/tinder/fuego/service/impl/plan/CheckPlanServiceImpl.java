/**   
* @Title: CheckPlanServiceImpl.java 
* @Package cn.tinder.fuego.service.impl.plan 
* @Description: TODO
* @author Tang Jun   
* @date 2013-10-5 上午03:23:11 
* @version V1.0   
*/ 
package cn.tinder.fuego.service.impl.plan;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.tinder.fuego.dao.CheckPlanDao;
import cn.tinder.fuego.dao.DaoContext;
import cn.tinder.fuego.dao.SystemUserDao;
import cn.tinder.fuego.dao.TransEventDao;
import cn.tinder.fuego.domain.po.CheckPlan;
import cn.tinder.fuego.domain.po.PhysicalAssetsStatus;
import cn.tinder.fuego.domain.po.ReceivePlan;
import cn.tinder.fuego.domain.po.SystemUser;
import cn.tinder.fuego.domain.po.TransEvent;
import cn.tinder.fuego.service.AssetsManageService;
import cn.tinder.fuego.service.ServiceContext;
import cn.tinder.fuego.service.TransPlanService;
import cn.tinder.fuego.service.constant.TransactionConst;
import cn.tinder.fuego.service.constant.UserRoleConst;
import cn.tinder.fuego.service.impl.TransactionServiceImpl;
import cn.tinder.fuego.service.model.convert.ConvertAssetsModel;
import cn.tinder.fuego.webservice.struts.bo.assets.AssetsInfoBo;
import cn.tinder.fuego.webservice.struts.bo.check.CheckPlanBo;
import cn.tinder.fuego.webservice.struts.bo.check.CheckPlanInfoBo;
import cn.tinder.fuego.webservice.struts.bo.check.CheckTransBo;
import cn.tinder.fuego.webservice.struts.bo.receive.ReceivePlanBo;
import cn.tinder.fuego.webservice.struts.bo.receive.ReceiveTransBo;
import cn.tinder.fuego.webservice.struts.bo.trans.TransactionBaseInfoBo;

/** 
 * @ClassName: CheckPlanServiceImpl 
 * @Description: TODO
 * @author Tang Jun
 * @date 2013-10-5 上午03:23:11 
 *  
 */

public class CheckPlanServiceImpl<E> extends TransactionServiceImpl implements TransPlanService<E>
{
	private static final Log log = LogFactory.getLog(CheckPlanServiceImpl.class);

	private TransEventDao transEventDao = DaoContext.getInstance().getTransEventDao();

	private SystemUserDao systemUserDao = DaoContext.getInstance().getSystemUserDao();
	private CheckPlanDao checkPlanDao = DaoContext.getInstance().getCheckPlanDao();
	private AssetsManageService assetsManageService = ServiceContext.getInstance().getAssetsManageService();

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#createPlan(java.lang.String)
	 */
	@Override
	public E createPlan(String user)
	{
		CheckPlanBo plan = new CheckPlanBo();

		TransactionBaseInfoBo parentTrans = super.createTransByUserAndType(user,user, TransactionConst.CHECK_PLAN_TYPE,null);
		
		
		List<SystemUser> gasUserList = systemUserDao.getUserByRole(UserRoleConst.GASSTATION);
 		
		//create a transaction for every gas station
		for(SystemUser gasUser : gasUserList)
		{

 			 List<AssetsInfoBo> planList = assetsManageService.getAssetsByDept(gasUser.getDepartment());
  			 
 			 if(null != planList && !planList.isEmpty())
 			 { 
 	 			 TransactionBaseInfoBo trans = super.createTransByUserAndType(user,gasUser.getUserName(), TransactionConst.CHECK_PLAN_TYPE,parentTrans.getTransID());
 				 List<CheckPlan> checkPlanList = convertCheckPlanByBo(trans.getTransID(), planList);
 	 	  		 checkPlanDao.create(checkPlanList);
 	 			plan.getPlanInfo().getAssetsPage().getAssetsList().addAll(planList);
 			 }
 			 else
 			 {
 				 log.warn("the assests of " + gasUser + "is 0,no need to create a transaction.");
 			 }

		}
  		plan.getTransInfo().setTransInfo(parentTrans);
		return (E)plan;
	}

	private List<CheckPlan> convertCheckPlanByBo(String transID, List<AssetsInfoBo> assestsInfoList)
	{
		List<CheckPlan> planList = new ArrayList<CheckPlan>();
		for(AssetsInfoBo assetsBo : assestsInfoList)
		{	
			CheckPlan checkPlan = new CheckPlan();
			checkPlan.setTransID(transID);
			checkPlan.setAssetsID(assetsBo.getAssets().getAssetsID());
			checkPlan.setDeptID(assetsBo.getAssets().getDuty());
			checkPlan.setAssetsName(assetsBo.getAssets().getAssetsName());
			checkPlan.setManufacture(assetsBo.getAssets().getManufacture());
			checkPlan.setSpec(assetsBo.getAssets().getSpec());
			checkPlan.setQuantity(assetsBo.getAssets().getQuantity());
			checkPlan.setCheckCnt(assetsBo.getExtAttr().getCheckCnt());
			checkPlan.setCheckState(assetsBo.getExtAttr().getCheckState());
			checkPlan.setNote(assetsBo.getExtAttr().getNote());
			planList.add(checkPlan);
		}

		return planList;
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#deletePlan(java.lang.String)
	 */
	@Override
	public void deletePlan(String transID)
	{
		super.deleteTransByID(transID);
		checkPlanDao.deleteByTransID(transID);
		
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#updatePlan(java.lang.Object)
	 */
	@Override
	public void updatePlan(E plan)
	{
		CheckPlanBo checkPlan = (CheckPlanBo)plan;
		
		List<CheckPlan> planList = new ArrayList<CheckPlan>();

		List<CheckTransBo>  childList = checkPlan.getTransInfo().getChildTransList();
		if(null == childList)
		{
			log.warn("the child is null,is not a parent transaction");
			checkPlanDao.deleteByTransID(checkPlan.getTransInfo().getTransInfo().getTransID());
			planList = convertCheckPlanByBo(checkPlan.getTransInfo().getTransInfo().getTransID(),checkPlan.getPlanInfo().getAssetsPage().getAssetsList());
			if(checkPlan.getPlanInfo().getAssetsPage().isCheckFinished())
			{
				this.forwardNext(checkPlan.getTransInfo().getTransInfo().getTransID());
			}
		}
		else
		{
			Map<String,List<AssetsInfoBo>> deptMapAssestList = ConvertAssetsModel.convertAssestsListBoToDeptMap(checkPlan.getPlanInfo().getAssetsPage().getAssetsList());

 
			
 			//get all the plan for every child transaction
			for(CheckTransBo childTrans : childList)
			{
				planList.addAll(convertCheckPlanByBo(childTrans.getTransInfo().getTransID(),deptMapAssestList.get(childTrans.getTransInfo().getHandleUser())));
 			}
			//add the receive information in database.
			
		}
		checkPlanDao.create(planList);
		
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#forwardNext(java.lang.String)
	 */
	@Override
	public void forwardNext(String transID)
	{
		TransEvent transEvent =transEventDao.getByTransID(transID);

		String handleUser;
		switch(transEvent.getCurrentStep())
		{
		case 1 :
		    handleUser = transEvent.getHandleUser();
		    break;
		default :
			handleUser = transEvent.getCreateUser();
			log.warn("the step i unexpected. step is" + transEvent.getCurrentStep());
			
		}
		super.forwardNext(transID, handleUser);
		
		
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#getPlanByTransID(java.lang.String)
	 */
	@Override
	public E getPlanByTransID(String transID)
	{
		TransactionBaseInfoBo baseTrans = super.getTransByID(transID);
		if(null == baseTrans)
		{
			return null;
		}
		CheckPlanBo plan = new CheckPlanBo();
		CheckTransBo checkTrans = new CheckTransBo();
		checkTrans.setTransInfo(baseTrans);
		plan.setTransInfo(checkTrans);

		List<TransEvent> childEventList = transEventDao.getTransByParentID(transID);

		 for(TransEvent event : childEventList)
		 {
			 if(null == checkTrans.getChildTransList())
			 {
				 checkTrans.setChildTransList(new ArrayList<CheckTransBo>());
			 }
			CheckTransBo child = new CheckTransBo();
			child.setTransInfo(super.getTransByID(event.getTransID()));
			checkTrans.getChildTransList().add(child);
		 } 
		

 		List<CheckPlan> checkPlanList = getCheckPlanListByTranID(transID);
		for(CheckPlan checkPlan : checkPlanList)
		{
			plan.getPlanInfo().getAssetsPage().getAssetsList().add(convertCheckPlan(checkPlan));
		}
 		return (E) plan;
	}

	private AssetsInfoBo convertCheckPlan(CheckPlan checkPlan)
	{
		AssetsInfoBo assetsInfo ;
		assetsInfo = assetsManageService.getAssetsByAssetID(checkPlan.getAssetsID());
		
		if(null == assetsInfo)
		{
			assetsInfo = new AssetsInfoBo();
			assetsInfo.getAssets().setAssetsID(checkPlan.getAssetsID());
			assetsInfo.getAssets().setAssetsName(checkPlan.getAssetsName());
			assetsInfo.getAssets().setManufacture(checkPlan.getManufacture());
			assetsInfo.getAssets().setSpec(checkPlan.getSpec());
			assetsInfo.getAssets().setDuty(checkPlan.getDeptID());
		}
 
		assetsInfo.getExtAttr().setCheckState(checkPlan.getCheckState());
		assetsInfo.getExtAttr().setCheckCnt(checkPlan.getCheckCnt());
		assetsInfo.getExtAttr().getNote();

		return assetsInfo;
	}

	private List<CheckPlan>  getCheckPlanListByTranID(String transID)
	{
		List<TransEvent> childEventList = transEventDao.getTransByParentID(transID);
		 
		 List<String> childEventIDList = new ArrayList<String>();
		 for(TransEvent event : childEventList)
		 {
			 childEventIDList.add(event.getTransID());
		 }
		 
		 List<CheckPlan> checkPlanList;
		 // the check transaction is child 
		 if(null == childEventList || childEventList.isEmpty())
		 {
			 checkPlanList = checkPlanDao.getByTransID(transID);
		 }
		 else
		 {
			 checkPlanList = checkPlanDao.getByTransIDList(childEventIDList);
		 }
		 return checkPlanList;
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#getExportFile(java.lang.Object)
	 */
	@Override
	public File getExportFile(E plan)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#backward(java.lang.String)
	 */
	@Override
	public void backward(String transID)
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#validate(java.lang.Object)
	 */
	@Override
	public void validate(E plan)
	{
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see cn.tinder.fuego.service.TransPlanService#createPlan(java.lang.String, java.util.List)
	 */
	@Override
	public E createPlan(String user, List<String> childUserList)
	{
		// TODO Auto-generated method stub
		return null;
	}

}
