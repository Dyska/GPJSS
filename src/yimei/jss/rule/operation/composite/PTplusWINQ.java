package yimei.jss.rule.operation.composite;

import yimei.jss.jobshop.OperationOption;
import yimei.jss.jobshop.WorkCenter;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.simulation.state.SystemState;

/**
 * Created by YiMei on 27/09/16.
 */
public class PTplusWINQ extends AbstractRule {

    public PTplusWINQ(RuleType t) {
        name = "\"PT+WINQ\"";
        this.type = t;
    }

    @Override
    public double priority(OperationOption op, WorkCenter workCenter, SystemState systemState) {
        return op.getProcTime() + systemState.workInNextQueue(op);
    }
}
