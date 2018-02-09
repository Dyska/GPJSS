package yimei.jss.niching;

/**
 * Created by dyska on 8/02/18.
 */
public interface ClearableEvaluator {
    void setClear(boolean clear);
    double getRadius();
    PhenoCharacterisation getPhenoCharacterisation(int index);
}
