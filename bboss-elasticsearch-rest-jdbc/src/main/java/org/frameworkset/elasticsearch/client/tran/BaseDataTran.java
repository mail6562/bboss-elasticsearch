package org.frameworkset.elasticsearch.client.tran;

import org.frameworkset.elasticsearch.ElasticSearchException;
import org.frameworkset.elasticsearch.client.ESDataImportException;
import org.frameworkset.elasticsearch.client.ImportCount;
import org.frameworkset.elasticsearch.client.TranErrorWrapper;
import org.frameworkset.elasticsearch.client.context.ImportContext;
import org.frameworkset.elasticsearch.client.schedule.ImportIncreamentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class BaseDataTran implements DataTran{
	private static Logger logger = LoggerFactory.getLogger(BaseDataTran.class);
	protected static Object dummy = new Object();
	protected ImportContext importContext;
	protected TranResultSet jdbcResultSet;
	public BaseDataTran(TranResultSet jdbcResultSet,ImportContext importContext) {
		this.jdbcResultSet = jdbcResultSet;
		this.importContext = importContext;
	}



//	public BaseDataTran(String esCluster) {
//		clientInterface = ElasticSearchHelper.getRestClientUtil(esCluster);
//	}



	protected void stop(){

		importContext.stop();

	}

	public String tran() throws ElasticSearchException {
		this.importContext = importContext;
		if(jdbcResultSet == null)
			return null;
		if(isPrintTaskLog()) {
			logger.info(new StringBuilder().append("import data to IndexName[").append(importContext.getEsIndexWrapper().getIndex())
							.append("] IndexType[").append(importContext.getEsIndexWrapper().getType())
							.append("] start.").toString());
		}
		if (importContext.getStoreBatchSize() <= 0) {
			return serialExecute(        );
		} else {
			if(importContext.getThreadCount() > 0 && importContext.isParallel()){
				return this.parallelBatchExecute( );
			}
			else{
				return this.batchExecute(  );
			}

		}

	}
	protected void jobComplete(ExecutorService service,Exception exception,Object lastValue ,TranErrorWrapper tranErrorWrapper){
		if (importContext.getScheduleService() == null) {//作业定时调度执行的话，需要关闭线程池
			service.shutdown();
		}
		else{
			if(tranErrorWrapper.assertCondition(exception)){
				importContext.flushLastValue( lastValue );
			}
			else{
				service.shutdown();
				this.stop();
			}
		}
	}
	protected boolean isPrintTaskLog(){
		return importContext.isPrintTaskLog() && logger.isInfoEnabled();
	}
	public void waitTasksComplete(final List<Future> tasks,
								   final ExecutorService service,Exception exception,Object lastValue,final ImportCount totalCount ,final TranErrorWrapper tranErrorWrapper ){
		if(!importContext.isAsyn() || importContext.getScheduleService() != null) {
			int count = 0;
			for (Future future : tasks) {
				try {
					future.get();
					count ++;
				} catch (ExecutionException e) {
					if(exception == null)
						exception = e;
					if( logger.isErrorEnabled()) {
						if (e.getCause() != null)
							logger.error("", e.getCause());
						else
							logger.error("", e);
					}
				}catch (Exception e) {
					if(exception == null)
						exception = e;
					if( logger.isErrorEnabled()) logger.error("",e);
				}
			}
			if(isPrintTaskLog()) {
				logger.info(new StringBuilder().append("Complete tasks:")
						.append(count).append(",Total import ")
						.append(totalCount.getTotalCount()).append(" records.").toString());
			}
			jobComplete(  service,exception,lastValue ,tranErrorWrapper);
		}
		else{
			Thread completeThread = new Thread(new Runnable() {
				@Override
				public void run() {
					int count = 0;
					for (Future future : tasks) {
						try {
							future.get();
							count ++;
						} catch (ExecutionException e) {
							if( logger.isErrorEnabled()) {
								if (e.getCause() != null)
									logger.error("", e.getCause());
								else
									logger.error("", e);
							}
						}catch (Exception e) {
							if( logger.isErrorEnabled()) logger.error("",e);
						}
					}
					if(isPrintTaskLog()) {
						logger.info(new StringBuilder().append("Complete tasks:")
								.append(count).append(",Total import ")
								.append(totalCount.getTotalCount())
								.append(" records.").toString());
					}
					jobComplete(  service,null,null,tranErrorWrapper);
				}
			});
			completeThread.start();
		}
	}



	public static final Class[] basePrimaryTypes = new Class[]{Integer.TYPE, Long.TYPE,
								Boolean.TYPE, Float.TYPE, Short.TYPE, Double.TYPE,
								Character.TYPE, Byte.TYPE, BigInteger.class, BigDecimal.class};

	public static boolean isBasePrimaryType(Class type) {
		if (!type.isArray()) {
			if (type.isEnum()) {
				return true;
			} else {
				Class[] var1 = basePrimaryTypes;
				int var2 = var1.length;

				for(int var3 = 0; var3 < var2; ++var3) {
					Class primaryType = var1[var3];
					if (primaryType.isAssignableFrom(type)) {
						return true;
					}
				}

				return false;
			}
		} else {
			return false;
		}
	}




	public Object getLastValue() throws ESDataImportException {


		if(importContext.getLastValueClumnName() == null){
			return null;
		}

//			if (this.importIncreamentConfig.getDateLastValueColumn() != null) {
//				return this.getValue(this.importIncreamentConfig.getDateLastValueColumn());
//			} else if (this.importIncreamentConfig.getNumberLastValueColumn() != null) {
//				return this.getValue(this.importIncreamentConfig.getNumberLastValueColumn());
//			}
//			else if (this.dataTranPlugin.getSqlInfo().getLastValueVarName() != null) {
//				return this.getValue(this.dataTranPlugin.getSqlInfo().getLastValueVarName());
//			}
		try {
			if (importContext.getLastValueType() == null || importContext.getLastValueType().intValue() == ImportIncreamentConfig.NUMBER_TYPE)
				return jdbcResultSet.getValue(importContext.getLastValueClumnName());
			else if (importContext.getLastValueType().intValue() == ImportIncreamentConfig.TIMESTAMP_TYPE) {
				return jdbcResultSet.getDateTimeValue(importContext.getLastValueClumnName());
			}
		}
		catch (ESDataImportException e){
			throw (e);
		}
		catch (Exception e){
			throw new ESDataImportException(e);
		}
		return null;


	}

}
