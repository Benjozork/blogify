/* tslint:disable:no-shadowed-variable */
import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Article} from '../../models/Article';
import {AuthService} from '../auth/auth.service';
import * as uuid from 'uuid/v4';

@Injectable({
    providedIn: 'root'
})
export class ArticleService {

    constructor(private httpClient: HttpClient, private authService: AuthService) {
    }

    async getAllArticles(fields: string[] = [], amount: number = 25): Promise<Article[]> {
        const articlesObs = this.httpClient.get<Article[]>(`/api/articles/?fields=${fields.join(',')}&amount=${amount}`);
        const articles = await articlesObs.toPromise();
        const userUUIDs = new Set<string>();
        articles.forEach(it => {
            userUUIDs.add(it.createdBy.toString());
        });
        const users = await Promise.all(([...userUUIDs]).map(it => this.authService.fetchUser(it.toString())));
        const out: Article[] = [];
        articles.forEach((article) => {
            const copy = article;
            copy.createdBy = users.find((user) => user.uuid === article.createdBy.toString());
            out.push(copy);
        });
        return out;
    }

    async getArticleByUUID(uuid: string, fields: string[] = []): Promise<Article> {
        const article =  await this.httpClient.get<Article>(`/api/articles/${uuid}?fields=${fields.join(',')}`).toPromise();
        article.createdBy = await this.authService.fetchUser(article.createdBy.toString());
        return article;
    }

    async getArticleByForUser(uuid: string, fields: string[] = []): Promise<Article[]> {
        return this.httpClient.get<Article[]>(`/api/articles/forUser/${uuid}?fields=${fields.join(',')}`).toPromise();
    }

    async createNewArticle(article: Article, userToken: string = this.authService.userToken): Promise<object> {

        const httpOptions = {
            headers: new HttpHeaders({
                'Content-Type': 'application/json',
                Authorization: `Bearer ${userToken}`
            })
        };

        const {content, title, summary, categories} = article;

        const newArticle = {
            uuid: uuid(),
            content,
            title,
            summary,
            categories,
            createdBy: this.authService.userUUID,
        };

        return this.httpClient.post(`/api/articles/`, newArticle, httpOptions).toPromise();
    }

    // noinspection JSUnusedGlobalSymbols
    updateArticle(uuid: string, article: Article, userToken: string = this.authService.userToken) {
        const httpOptions = {
            headers: new HttpHeaders({
                'Content-Type': 'application/json',
                Authorization: `Bearer ${userToken}`
            })
        };

        return this.httpClient.patch<Article>(`/api/articles/${uuid}`, article, httpOptions);
    }

    // noinspection JSUnusedGlobalSymbols
    deleteArticle(uuid: string, userToken: string = this.authService.userToken) {
        const httpOptions = {
            headers: new HttpHeaders({
                'Content-Type': 'application/json',
                Authorization: `Bearer ${userToken}`
            })
        };

        return this.httpClient.delete(`api/articles/${uuid}`, httpOptions);
    }

}
