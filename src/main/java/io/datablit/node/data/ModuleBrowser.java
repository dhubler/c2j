package org.conf2.data;

import org.conf2.CodedError;
import org.conf2.schema.*;
import org.conf2.schema.yang.YangModule;

import java.util.Iterator;

public class ModuleBrowser {
    private Module yang = YangModule.YANG;
    public Module module;
    private boolean resolve;

    public ModuleBrowser(Module module, boolean resolve) {
        this.module = module;
        this.resolve = resolve;
    }

    public MetaCollection getSchema() {
        return yang;
    }

    public Node getNode() {
        MyNode n = new MyNode();
        n.onSelect = (ContainerRequest r) -> {
            if (r.create) {
                module = new Module("unknown");
            }
            if (module != null) {
                return enterModule(module);
            }
            return null;
        };
        return n;
    }

    Node enterModule(final Module module) {
        Extend n = new Extend(selectCollection(module));
        n.onSelect = (Node base, ContainerRequest r) -> {
            switch ( r.meta.getIdent()) {
                case "revision":
                    if (r.create) {
                        module.revision = new Revision("unknown");
                    }
                    if (module.revision != null) {
                        return enterRevision(module.revision);
                    }
                    return null;
            }
            return base.select(r);
        };
        return n;
    }

    Node selectCollection(MetaCollection c) {
        Extend n = new Extend(Marshal.container(c));
        n.onSelect = (Node base, ContainerRequest r) -> {
            switch (r.meta.getIdent()) {
                case "groupings":
                    if (!resolve) {
                        HasGroupings m = ((HasGroupings)c);
                        if (r.create || !MetaUtil.empty(m.getGroupings())) {
                            return selectCollectionList(m.getGroupings(), r.meta.getIdent());
                        }
                    }
                    break;
                case "typedefs":
                    if (!resolve) {
                        HasTypedefs m = ((HasTypedefs) c);
                        if (r.create || !MetaUtil.empty(m.getTypedefs())) {
                            return selectCollectionList(m.getTypedefs(), r.meta.getIdent());
                        }
                    }
                    break;
                case "definitions":
                    if  (r.create || ! MetaUtil.empty(c)) {
                        return selectCollectionList(c, r.meta.getIdent());
                    }
                    break;
                case "rpcs":
                case "actions":
                    HasActions hasActions = ((HasActions) c);
                    if (r.create || !MetaUtil.empty(hasActions.getRpcs())) {
                        return selectCollectionList(hasActions.getRpcs(), r.meta.getIdent());
                    }
                case "notifications":
                    if (!resolve && c instanceof HasNotifications) {
                        HasNotifications hasNots = ((HasNotifications) c);
                        if (r.create || !MetaUtil.empty(hasNots.getNotifications())) {
                            return selectCollectionList(hasNots.getNotifications(), r.meta.getIdent());
                        }
                    }
                    break;
            }
            return base.select(r);
        };
        return n;
    }

    Node selectCollectionList(MetaCollection collection, String type) {
        MyNode n = new MyNode();
        final Iterator<Meta>[] iPtr = new Iterator[1];
        n.onNext = (ListRequest r) -> {
            Value[] key = r.key;
            Meta m = null;
            if (r.create) {
                switch (type) {
                    case "definitions":
                    case "groupings":
                        m = new Grouping(r.key[0].str);
                        break;
                    case "typedefs":
                        m = new Typedef(r.key[0].str);
                        break;
                    case "notifications":
                        m = new Notification(r.key[0].str);
                        break;
                    case "rpcs":
                    case "actions":
                        m = new Rpc(r.key[0].str);
                }
            } else {
                if (key.length > 0) {
                    m = MetaUtil.findByIdent(collection, key[0].str);
                } else {
                    if (r.isFirst) {
                        iPtr[0] = new MetaCollectionIterator(collection);
                    }
                    if (iPtr[0].hasNext()) {
                        m = iPtr[0].next();
                    }
                }
            }
            if (m != null) {
                Node next = null;
                switch (type) {
                    case "typedefs":
                        next = enterTypedef((Typedef)m);
                        break;
                    default:
                        next = selectCollection((MetaCollection)m);
                        break;
                }
                return new NodeKeyPair(next, key);
            }
            return null;
        };
        return n;
    }

    Node selectMeta(Meta data) {
        return Marshal.container(data);
    }

    Node enterLeafable(Leafable leaf) {
        Extend n = new Extend(selectMeta(leaf));
        n.onSelect = (Node base, ContainerRequest r) -> {
            switch (r.meta.getIdent()) {
                case "type":
                    if (r.create) {
                        leaf.setDataType(new DataType(leaf, "unknown"));
                    }
                    if (leaf.getDataType() != null) {
                        return enterDataType(leaf.getDataType());
                    }
                    return null;
            }
            return base.select(r);
        };
        return n;
    }

    Node enterDataType(DataType type) {
        Extend n = new Extend(selectMeta(type));
        n.onWrite = (Node base, FieldRequest r, Value val) -> {
            switch (r.meta.getIdent()) {
                case "ident":
                    type.setIdent(val.str);
                    return;
            }
            base.write(r, val);
        };
        return n;
    }

    Node enterUses(Uses uses) {
        // TODO n.select
        return selectMeta(uses);
    }

    Node enterChoice(Choice choice) {
        Extend n = new Extend(selectMeta(choice));
        n.onSelect = (Node base, ContainerRequest r) -> {
            String ident = r.meta.getIdent();
            if ("cases".equals(ident)) {
                return selectCollection(choice);
            }
            return null;
        };
        return n;
    }

    Meta createDefinition(MetaCollection parent, String type) {
        Meta child = null;
        String ident = "unknown";
        switch (type) {
            case "leaf":
                child = new Leaf(ident);
                break;
            case "anyxml":
                child = new Any(ident);
                break;
            case "leaf-node":
                child = new LeafList(ident);
                break;
            case "container":
                child = new Container(ident);
                break;
            case "node":
                child = new MetaList(ident);
                break;
            case "uses":
                child = new Uses(ident);
                break;
            case "grouping":
                child = new Grouping(ident);
                break;
            case "typedef":
                child = new Typedef(ident);
                break;
            case "rpc":
                child = new Rpc(ident);
                break;
            default:
                throw new RuntimeException("not implemenented");
        }
        parent.addMeta(child);
        return child;
    }

    String definitionType(Meta data) {
        if (data instanceof MetaList) {
            return "node";
        } else if (data instanceof Uses) {
            return "uses";
        } else if (data instanceof Choice) {
            return "choice";
        } else if (data instanceof Any) {
            return "anyxml";
        } else if (data instanceof Leaf) {
            return "leaf";
        } else if (data instanceof LeafList) {
            return "leaf-node";
        } else {
            return "container";
        }
    }

    Node selectDefinition(MetaCollection parent, Meta data) {
        MyNode n = new MyNode();
        final Meta[] dataPtr = new Meta[] { data };
        n.onChoose = (Selection sel, Choice choice) -> {
            String caseType = definitionType(data);
            ChoiceCase kase = choice.getCase(caseType);
            if (kase == null) {
                throw new CodedError("Case " + caseType + " not found", 404);
            }
            return kase;
        };
        n.onSelect = (ContainerRequest r) -> {
            String ident = r.meta.getIdent();
            if (r.create) {
                dataPtr[0] = createDefinition(parent, r.meta.getIdent());
            }
            if (dataPtr[0] == null) {
                return null;
            }
            switch (ident) {
                case "leaf-node":
                case "leaf":
                    return enterLeafable((Leafable)dataPtr[0]);
                case "uses":
                    return enterUses((Uses)dataPtr[0]);
                case "choice":
                    return enterChoice((Choice)dataPtr[0]);
                case "action":
                case "rpc":
                    return enterRpc((Rpc)dataPtr[0]);
                case "typedef":
                    return enterTypedef((Typedef)dataPtr[0]);
            }
            return selectCollection((MetaCollection) dataPtr[0]);
        };
        n.onWrite = (FieldRequest r, Value val) ->
                Reflect.setterMethod(r.meta, dataPtr[0], val);
        n.onRead = (FieldRequest r) ->
                Reflect.getterMethod(r.meta, dataPtr[0]);
        return n;
    }

    Node enterTypedef(Typedef typedef) {
        Extend n = new Extend(selectMeta(typedef));
        n.onSelect = (Node base, ContainerRequest r) -> {
            switch (r.meta.getIdent()) {
                case "type":
                    if (r.create) {
                        typedef.setDataType(new DataType(typedef, "unknown"));
                    }
                    if (typedef.getDataType() != null) {
                        return enterDataType(typedef.getDataType());
                    }
                    return null;
            }
            return base.select(r);
        };
        return n;
    }

    Node enterRpc(Rpc rpc) {
        Extend n = new Extend(selectMeta(rpc));
        n.onSelect = (Node base, ContainerRequest r) -> {
            switch (r.meta.getIdent()) {
                case "input":
                    if (r.create) {
                        rpc.input = new RpcInput();
                    }
                    if (rpc.input != null) {
                        return selectCollection(rpc.input);
                    }
                    return null;
                case "output":
                    if (r.create) {
                        rpc.output = new RpcOutput();
                    }
                    if (rpc.output != null) {
                        return selectCollection(rpc.output);
                    }
                    return null;
            }
            return base.select(r);
        };
        return n;
    }

    Node enterRevision(Revision rev) {
        Extend n = new Extend(selectMeta(rev));
        n.onRead = (Node base, FieldRequest r) -> {
            switch (r.meta.getIdent()) {
                case "rev-date":
                    return Value.Str(rev.getIdent());
            }
            return base.read(r);
        };
        n.onWrite = (Node base, FieldRequest r, Value val) -> {
            switch (r.meta.getIdent()) {
                case "rev-date":
                    rev.setIdent(val.str);
                    break;
            }
            base.write(r, val);
        };
        return n;
    }
}
