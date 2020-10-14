package org.abs_models.frontend.typechecker.locationtypes;

import org.abs_models.backend.common.InternalBackendException;
import org.abs_models.frontend.analyser.HasCogs;
import org.abs_models.frontend.analyser.SemanticConditionList;
import org.abs_models.frontend.ast.*;
import org.abs_models.frontend.parser.Main;
import org.abs_models.frontend.typechecker.Type;
import org.abs_models.frontend.typechecker.ext.AdaptDirection;
import org.abs_models.frontend.typechecker.ext.DefaultTypeSystemExtension;
import org.abs_models.frontend.typechecker.ext.TypeSystemExtension;
import org.sat4j.minisat.learning.ClauseOnlyLearning;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LocationTypeInferenceExtension extends DefaultTypeSystemExtension {
    private final Constraints constraints = new Constraints();
    private final Scope farTypeScope = Scope.CLASS_LOCAL_FAR;
    private Map<HasCogs, List<LocationType>> farTypes = new HashMap<>();

    private LocationType defaultType = LocationType.INFER;

    private boolean debug = false;

    public Map<LocationTypeVar, LocationType> getResults() {
        return results;
    }

    private Map<LocationTypeVar, LocationType> results;

    public LocationType getDefaultType() {
        return defaultType;
    }

    public void setDefaultType(LocationType defaultType) {
        this.defaultType = defaultType;
    }

    public void enableDebug() {
        this.debug = true;
        constraints.enableDebug();
    }

    public LocationTypeInferenceExtension(Model m) {
        super(m);
    }

    private LocationTypeVar adaptTo(LocationTypeVar expLocType, AdaptDirection dir, LocationTypeVar adaptTo, ASTNode<?> typeNode, ASTNode<?> originatingNode) {
        LocationTypeVar tv = new LocationTypeVar(typeNode);
        constraints.add(Constraint.adapt(tv, expLocType, dir, adaptTo, originatingNode));
        return tv;
    }

    @Override
    public void checkEq(Type lt, Type t, ASTNode<?> origin) {
        LocationTypeVar lv1 = getVar(lt);
        LocationTypeVar lv2 = getVar(t);
        Constraint c = Constraint.eq(lv1, lv2, origin);
        constraints.add(c);
    }

    @Override
    public void checkAssignable(Type adaptTo, AdaptDirection dir, Type rht, Type lht, ASTNode<?> n) {
        LocationTypeVar lhv = getVar(lht);
        LocationTypeVar rhv = getVar(rht);

        if (adaptTo != null && getVar(adaptTo) != LocationTypeVar.NEAR) {
            rhv = adaptTo(rhv, dir, getVar(adaptTo), null, n);
        }
        constraints.add(Constraint.sub(rhv, lhv, n));
    }

    private LocationTypeVar addNewVar(Type t, ASTNode<?> originatingNode, ASTNode<?> typeNode) {
        LocationTypeExtension.getLocationTypeFromAnnotations(t); // check consistency of annotations
        LocationTypeVar ltv = getVar(t);
        if (ltv != null)
            return ltv;
        LocationType lt = getLocationTypeOrDefault(t, originatingNode);
        LocationTypeVar tv;
        if (lt.isInfer()) {
            tv = new LocationTypeVar(originatingNode);
        } else {
            tv = LocationTypeVar.getFromLocationType(lt);
        }
        annotateVar(t, tv);
        return tv;
    }

    private void adaptAndSet(Type rht, AdaptDirection dir, LocationTypeVar adaptTo, ASTNode<?> origin) {
        if (adaptTo != LocationTypeVar.NEAR) {
            LocationTypeVar rlv = getVar(rht);
            LocationTypeVar adapted = adaptTo(rlv, dir, adaptTo, null, origin);
            annotateVar(rht, adapted);
        }
    }

    private void annotateVar(Type t, LocationTypeVar lv) {
        LocationTypeVar.setVar(t, lv);
    }

    @Override
    public void annotateType(Type t, ASTNode<?> on, ASTNode<?> typeNode) {
        if (on instanceof SyncCall) {
        } else if (on instanceof AsyncCall) {
            AsyncCall ac = (AsyncCall) on;
            adaptAndSet(t, AdaptDirection.FROM, getVar(ac.getCallee().getType()), on);
        } else if (on instanceof AwaitAsyncCall) {
            AwaitAsyncCall ac = (AwaitAsyncCall) on;
            adaptAndSet(t, AdaptDirection.FROM, getVar(ac.getCallee().getType()), on);
        } else if (on instanceof ThisExp) {
            annotateVar(t, LocationTypeVar.NEAR);
        } else if (on instanceof NewExp) {
            NewExp ne = (NewExp) on;
            LocationTypeVar lv;
            if (ne.hasLocal()) {
                lv = LocationTypeVar.NEAR;
            } else {
                lv = LocationTypeVar.FAR;
                // TODO
            }
            annotateVar(t, lv);
        } else {
            if (t.isReferenceType()) {
                addNewVar(t, on, typeNode);
            }
            if (t.isNullType()) {
                annotateVar(t, LocationTypeVar.BOTTOM);
            }
        }
    }

    private List<LocationType> getFarTypes(ASTNode<?> origin) {
        HasCogs node=null;
        Scope s = farTypeScope;
        switch (s) {
            case GLOBAL_FAR:
                node = origin.getCompilationUnit().getModel();
                break;
            case COMPILATION_UNIT_LOCAL_FAR:
                node = origin.getCompilationUnit();
                break;
            case MODULE_LOCAL_FAR:
                node = origin.getModuleDecl();
                break;
            case CLASS_LOCAL_FAR: {
                Decl d = origin.getContextDecl();
                if (d instanceof ClassDecl)
                    node = d;
                Block b = origin.getContextBlock();
                if (b instanceof MainBlock)
                    node = b;
                break;
            }
            case METHOD_LOCAL_FAR: {
                Block b = origin.getContextBlock();
                if (b != null)
                    node = b;
                break;
            }
        }
        if (node == null)
            return new ArrayList<>();
        List<LocationType> e = farTypes.get(node);
        if (e != null) {
            return e;
        }
        List<LocationType> result = new ArrayList<>();
        int numOfNewCogs = node.getNumberOfNewCogExpr();
        for (int i = 0; i < numOfNewCogs; i++) {
            result.add(LocationType.createParametricFar(s, i));
        }
        farTypes.put(node, result);
        return result;
    }

    @Override
    public void checkMethodCall(Call call) {
        LocationTypeVar lv = getVar(call.getCallee().getType());
        assert lv != null;
        if (call instanceof SyncCall) {
            constraints.add(Constraint.eq(lv, LocationTypeVar.NEAR, call));
        }
    }

    public static LocationTypeVar getVar(Type t) {
        return LocationTypeVar.getVar(t);
    }

    private LocationType getLocationTypeOrDefault(Type t, ASTNode<?> originatingNode) {
        LocationType lt = LocationTypeExtension.getLocationTypeFromAnnotations(t);
        if (lt == null) {
            return defaultType;
        }
        return lt;
    }

    @Override
    public void finished() {
        ConstraintSolver solver = new ConstraintSolver(constraints, debug);
        results = solver.solve();

        if (debug) {
            for (Map.Entry<LocationTypeVar, LocationType> e : results.entrySet()) {
                System.out.println("" + e.getKey() + " := " + e.getValue());
            }
        }

        if (!errors.containsErrors()) {
            SemanticConditionList sel = new SemanticConditionList();
            List<TypeSystemExtension> exts = model.getTypeExt().getTypeSystemExtensionList();
            model.getTypeExt().clearTypeSystemExtensions();
            LocationTypeExtension lte = new LocationTypeExtension(model, results);
            lte.setDefaultType(defaultType);
            model.getTypeExt().register(lte);
            model.typeCheck(sel);
            errors.addAll(sel);
            model.getTypeExt().clearTypeSystemExtensions();
            model.getTypeExt().registerAll(exts);
        }
    }
}
