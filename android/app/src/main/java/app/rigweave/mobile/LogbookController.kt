package app.rigweave.mobile

import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class LogbookController(private val repository: LogbookRepository) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val generation=AtomicLong(0)
    private var job:Job?=null
    private var signal:CancellationSignal?=null
    private var cursor:LogbookCursor?=null
    private val pages=mutableListOf<List<Qso>>()
    private val cursors=mutableListOf<LogbookCursor?>()
    private var exactTotal:Int?=null
    private var more=false
    var state by mutableStateOf<LogbookQueryState>(LogbookQueryState.Idle);private set
    var appliedFilter by mutableStateOf(LogbookFilter(limit=50));private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet());private set
    var stationId:String?=null;private set
    var pageSize by mutableStateOf(50);private set
    var pageIndex by mutableStateOf(0);private set

    fun apply(filter:LogbookFilter,station:String?=stationId){appliedFilter=filter.copy(limit=filter.limit.coerceIn(1,250));stationId=station;pageSize=appliedFilter.limit;load(reset=true,debounceMs=250)}
    fun retry()=load(reset=true)
    fun reset(){selectedIds=emptySet();apply(LogbookFilter(limit=50),stationId)}
    fun loadNext(){val ready=state as? LogbookQueryState.Ready?:return;if(!ready.hasMore)return;load(reset=false)}
    fun loadPrevious(){if(pageIndex<=0)return;pageIndex--;cursor=cursors.getOrNull(pageIndex);state=LogbookQueryState.Ready(pages[pageIndex],exactTotal,true)}
    fun toggleSelection(id:String){selectedIds=if(id in selectedIds)selectedIds-id else selectedIds+id}
    fun clearSelection(){selectedIds=emptySet()}

    private fun load(reset:Boolean,debounceMs:Long=0){
        val request=generation.incrementAndGet();signal?.cancel();job?.cancel();signal=CancellationSignal()
        val existing=(state as? LogbookQueryState.Ready)?.rows.orEmpty()
        if(reset){cursor=null;pages.clear();cursors.clear();pageIndex=0;exactTotal=null;state=LogbookQueryState.LoadingFirstPage}else state=LogbookQueryState.LoadingAnotherPage(existing)
        job=scope.launch{
            try{
                if(debounceMs>0)delay(debounceMs)
                val health=withContext(Dispatchers.IO){repository.health()}
                if(health.state!=ProjectionState.READY){if(request==generation.get())state=LogbookQueryState.ProjectionOptimising(health.progress);return@launch}
                val page=withContext(Dispatchers.IO){repository.page(appliedFilter,stationId,pageSize,if(reset)null else cursor,
                    offsetPage=if(reset)0 else pageIndex+1,exactCount=reset,signal=signal)}
                if(request!=generation.get())return@launch
                cursor=page.nextCursor
                if(reset){pages+=page.rows;cursors+=page.nextCursor}else{pageIndex++;if(pages.size>pageIndex)pages[pageIndex]=page.rows else pages+=page.rows;if(cursors.size>pageIndex)cursors[pageIndex]=page.nextCursor else cursors+=page.nextCursor}
                exactTotal=page.exactTotal?:exactTotal;more=page.hasMore
                state=if(page.rows.isEmpty())LogbookQueryState.Empty else LogbookQueryState.Ready(page.rows,exactTotal,page.hasMore)
            }catch(_:CancellationException){if(request==generation.get())state=LogbookQueryState.Cancelled}
            catch(error:Throwable){if(request==generation.get())state=LogbookQueryState.RecoverableError(error.message?:"Logbook query failed")}
        }
    }

    fun close(){signal?.cancel();scope.cancel()}
}
