package com.apdplat.module.security.model;

import com.apdplat.module.module.model.Command;
import com.apdplat.platform.model.Model;
import com.apdplat.module.module.model.Module;
import com.apdplat.platform.generator.ActionGenerator;
import com.apdplat.platform.annotation.ModelAttr;
import com.apdplat.platform.annotation.ModelAttrRef;
import com.apdplat.platform.annotation.ModelCollRef;
import com.apdplat.platform.service.ServiceFacade;
import com.apdplat.platform.util.SpringContextUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import org.compass.annotations.*;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Entity
@Scope("prototype")
@Component
@Searchable
@Table(name = "UserTable",
uniqueConstraints = {
    @UniqueConstraint(columnNames = {"username"})})
@XmlRootElement
@XmlType(name = "User")
public class User extends Model  implements UserDetails{
    @ManyToOne
    @SearchableComponent(prefix="org_")
    @ModelAttr("组织架构")
    @ModelAttrRef("orgName")
    protected Org org;

    //用户名不分词
    @SearchableProperty(index=Index.NOT_ANALYZED)
    @ModelAttr("用户名")
    protected String username;

    @SearchableProperty
    @ModelAttr("姓名")
    protected String realName;

    @ModelAttr("密码")
    protected String password;

    @SearchableProperty
    @ModelAttr("备注")
    protected String des;
    
    @ManyToMany(cascade = CascadeType.REFRESH, fetch = FetchType.LAZY)
    @JoinTable(name = "user_role", joinColumns = {
    @JoinColumn(name = "userID")}, inverseJoinColumns = {
    @JoinColumn(name = "roleID")})
    @OrderBy("id")
    @ModelAttr("用户拥有的角色列表")
    @ModelCollRef("roleName")
    protected List<Role> roles = new ArrayList<Role>();

    @ModelAttr("账号过期")
    protected boolean accountexpired = false;
    @ModelAttr("账户锁定")
    protected boolean accountlocked = false;
    @ModelAttr("信用过期")
    protected boolean credentialsexpired = false;
    @ModelAttr("账户可用")
    protected boolean enabled = true;

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    /**
     * 用户是否为超级管理员
     * @return
     */
    public boolean isSuperManager(){
        if(this.roles==null || this.roles.isEmpty())
        	return false;

        for(Role role : this.roles){
            if(role.isSuperManager())
                return true;
        }
        return false;
    }
    
    public String getRoleStrs(){
        if(this.roles==null || this.roles.isEmpty())
            return "";
        StringBuilder result=new StringBuilder();
        for(Role role : this.roles){
            result.append("role-").append(role.getId()).append(",");
        }
        result=result.deleteCharAt(result.length()-1);
        return result.toString();
    }

    public List<Command> getCommand() {
        List<Command> result = new ArrayList<Command>();

        if(this.roles==null || this.roles.isEmpty())
        	return result;

        //如果用户为超级管理员
        for (Role role : this.roles) {
            if (role.isSuperManager()) {
                return getAllCommand();
            }
        }
        //如果用户不是超级管理员则进行一下处理
        for (Role role : this.roles) {
            result.addAll(role.getCommands());
        }

        return result;
    }

    private List<Command> getAllCommand(){
    	ServiceFacade serviceFacade = SpringContextUtils.getBean("serviceFacade");
        List<Command> allCommand = serviceFacade.query(Command.class).getModels();
        return allCommand;
    }

    public List<Module> getModule() {
        List<Module> result = new  ArrayList<Module>();
        
        if(this.roles==null || this.roles.isEmpty())
        	return result;

        //如果用户为超级管理员
        for (Role role : this.roles) {
            if (role.isSuperManager()) {
            	return getAllModule();
            }
        }
        //如果用户不是超级管理员则进行一下处理
        for (Role role : this.roles) {
            result.addAll(assemblyModule(role.getCommands()));
        }

        return result;
    }
    private List<Module> getAllModule(){
    	ServiceFacade serviceFacade = SpringContextUtils.getBean("serviceFacade");
        List<Module> allModule = serviceFacade.query(Module.class).getModels();
        return allModule;
    }
    private List<Module> assemblyModule(List<Command> commands){
        List<Module> modules=new ArrayList<Module>();
        if(commands==null)
            return modules;
        
        for(Command command : commands){
            if(command!=null){
                Module module=command.getModule();
                if(module!=null){
                    modules.add(module);
                    assemblyModule(modules,module);
                }
            }
        }
        return modules;
    }
    private void assemblyModule(List<Module> modules,Module module){
        if(module!=null){
            Module parentModule=module.getParentModule();
            if(parentModule!=null){
                modules.add(parentModule);
                assemblyModule(modules,parentModule);
            }
        }
    }
    public String getAuthoritiesStr(){
        StringBuilder result=new StringBuilder();
        for(GrantedAuthority auth : getAuthorities()){
            result.append(auth.getAuthority()).append(",");
        }
        return result.toString();
    }
    /**
     * 获取授予用户的权利
     * @return
     */
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        if(this.roles==null || this.roles.isEmpty())
        	return null;

        Collection<GrantedAuthority> grantedAuthArray=new HashSet<GrantedAuthority>();

        log.debug("user privilege:");
        for (Role role : this.roles) {
            for (String priv : role.getAuthorities()) {
                log.debug(priv);
                grantedAuthArray.add(new GrantedAuthorityImpl(priv.toUpperCase()));
            }
        }
        grantedAuthArray.add(new GrantedAuthorityImpl("ROLE_MANAGER"));
        log.debug("ROLE_MANAGER");
        return grantedAuthArray;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !accountexpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountlocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !credentialsexpired;
    }

    @Override
    @XmlAttribute
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAccountexpired(boolean accountexpired) {
        this.accountexpired = accountexpired;
    }

    public void setAccountlocked(boolean accountlocked) {
        this.accountlocked = accountlocked;
    }

    public void setCredentialsexpired(boolean credentialsexpired) {
        this.credentialsexpired = credentialsexpired;
    }

    @XmlTransient
    public List<Role> getRoles() {
        return Collections.unmodifiableList(this.roles);
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public void clearRole() {
        this.roles.clear();
    }
    @Override
    @XmlAttribute
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @XmlAttribute
    public String getDes() {
        return des;
    }

    public void setDes(String des) {
        this.des = des;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @XmlTransient
    public Org getOrg() {
        return org;
    }

    public void setOrg(Org org) {
        this.org = org;
    }

    @Override
    @XmlAttribute
    public String getUsername() {
        return username;
    }
    @Override
    public String getMetaData() {
        return "用户信息";
    }

    public static void main(String[] args){
        User obj=new User();
        //生成Action
        ActionGenerator.generate(obj.getClass());
    }
}
